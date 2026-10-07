package com.cardenaspiero255.gamehubultra.ai

/**
 * Runtime health policy for external research providers.
 *
 * This protects latency and battery by temporarily quarantining providers that repeatedly
 * fail for infrastructure reasons. Semantic abstentions do not count as provider failures,
 * and successful evidence immediately restores the provider.
 */
data class UltraResearchProviderHealthPolicy(
    val failureThreshold: Int = 2,
    val cooldownMillis: Long = 30_000L
) {
    init {
        require(failureThreshold >= 1) { "Provider failure threshold must be positive." }
        require(cooldownMillis >= 1L) { "Provider cooldown must be positive." }
    }
}

class UltraResearchProviderHealth(
    private val policy: UltraResearchProviderHealthPolicy =
        UltraResearchProviderHealthPolicy()
) {
    private data class State(
        val consecutiveFailures: Int,
        val openUntilMillis: Long
    )

    private val states = mutableMapOf<String, State>()

    @Synchronized
    fun isAvailable(providerId: String, nowMillis: Long): Boolean {
        val id = providerId.trim()
        if (id.isBlank()) return true
        val state = states[id] ?: return true
        if (state.openUntilMillis <= 0L) return true
        if (nowMillis >= state.openUntilMillis) {
            states.remove(id)
            return true
        }
        return false
    }

    @Synchronized
    fun record(
        providerId: String,
        result: UltraProviderResult,
        nowMillis: Long
    ) {
        val id = providerId.trim()
        if (id.isBlank()) return

        if (result is UltraProviderResult.Evidence) {
            states.remove(id)
            return
        }

        if (!isInfrastructureFailure(result)) {
            return
        }

        val current = states[id]
        val failures = (current?.consecutiveFailures ?: 0) + 1
        val openUntil = if (failures >= policy.failureThreshold) {
            safeDeadline(nowMillis, policy.cooldownMillis)
        } else {
            0L
        }
        states[id] = State(
            consecutiveFailures = failures,
            openUntilMillis = openUntil
        )
    }

    private fun isInfrastructureFailure(result: UltraProviderResult): Boolean {
        val reasonCode = when (result) {
            is UltraProviderResult.Evidence -> return false
            is UltraProviderResult.Failure -> result.reasonCode
            is UltraProviderResult.Abstained -> result.reasonCode
        }
        return reasonCode in INFRASTRUCTURE_REASON_CODES
    }

    private fun safeDeadline(nowMillis: Long, cooldownMillis: Long): Long {
        val remaining = Long.MAX_VALUE - nowMillis
        return if (cooldownMillis > remaining) {
            Long.MAX_VALUE
        } else {
            nowMillis + cooldownMillis
        }
    }

    private companion object {
        val INFRASTRUCTURE_REASON_CODES = setOf(
            "PROVIDER_FAILURE",
            "PROVIDER_EXECUTION_FAILURE",
            "UPSTREAM_TIMEOUT",
            "UPSTREAM_UNAVAILABLE",
            "UPSTREAM_RATE_LIMIT",
            "UPSTREAM_HTTP_ERROR",
            "BACKEND_FAILURE",
            "BACKEND_HTTP_FAILURE",
            "BACKEND_NETWORK_FAILURE",
            "INVALID_BACKEND_RESPONSE"
        )
    }
}
