package com.cardenaspiero255.gamehubultra.ai

enum class UltraFrontierAuditEvent {
    PLAN_CREATED,
    LOCAL_ATTEMPT,
    RESEARCH_ATTEMPT,
    RETRY,
    REPLAN,
    ESCALATE_RESEARCH,
    ENSEMBLE_COMPARE,
    ACCEPT,
    FALLBACK_LOCAL,
    ABSTAIN,
    TOOL_BLOCKED,
    TOOL_EXECUTED
}

data class UltraFrontierAuditRecord(
    val correlationId: String,
    val lane: UltraFrontierLane,
    val event: UltraFrontierAuditEvent,
    val timestampMillis: Long,
    val attempt: Int? = null,
    val reasonCode: String? = null
)

/**
 * Bounded in-memory audit log for Frontier decisions.
 *
 * It intentionally stores only structured execution metadata. Prompts, answers,
 * memory contents, provider payloads and secrets are never accepted by this API.
 */
class UltraFrontierAuditTrail(
    private val capacity: Int = DEFAULT_CAPACITY,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private val lock = Any()
    private val records = ArrayDeque<UltraFrontierAuditRecord>()

    init {
        require(capacity >= 1) { "Frontier audit capacity must be positive." }
    }

    fun record(
        correlationId: String,
        lane: UltraFrontierLane,
        event: UltraFrontierAuditEvent,
        attempt: Int? = null,
        reasonCode: String? = null
    ) {
        val safeCorrelation = correlationId.trim().take(MAX_CORRELATION_LENGTH)
        if (safeCorrelation.isBlank()) return
        val safeReason = reasonCode
            ?.trim()
            ?.take(MAX_REASON_LENGTH)
            ?.takeIf(String::isNotBlank)
        val safeAttempt = attempt?.takeIf { it >= 1 }

        synchronized(lock) {
            while (records.size >= capacity) {
                records.removeFirst()
            }
            records.addLast(
                UltraFrontierAuditRecord(
                    correlationId = safeCorrelation,
                    lane = lane,
                    event = event,
                    timestampMillis = nowMillis(),
                    attempt = safeAttempt,
                    reasonCode = safeReason
                )
            )
        }
    }

    fun snapshot(): List<UltraFrontierAuditRecord> =
        synchronized(lock) { records.toList() }

    fun clear() {
        synchronized(lock) { records.clear() }
    }

    private companion object {
        const val DEFAULT_CAPACITY = 128
        const val MAX_CORRELATION_LENGTH = 96
        const val MAX_REASON_LENGTH = 80
    }
}
