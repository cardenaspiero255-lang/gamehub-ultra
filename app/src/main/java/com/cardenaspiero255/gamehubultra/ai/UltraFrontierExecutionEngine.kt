package com.cardenaspiero255.gamehubultra.ai

/**
 * Executes a planned Frontier query using the existing structured query coordinator.
 *
 * The engine can retry research when observable evidence is weak, but it never converts
 * stale/local text into a verified answer for a fresh-data request.
 */
class UltraFrontierExecutionEngine(
    private val coordinator: UltraQueryExecutionCoordinator,
    private val frontier: UltraFrontierOrchestrator = UltraFrontierOrchestrator(),
    private val critic: UltraFrontierCritic = UltraFrontierCritic(),
    private val networkAvailable: () -> Boolean = { true }
) {
    fun answer(
        request: UltraGeneralQueryRequest,
        localChat: () -> String?
    ): UltraQueryExecutionAnswer {
        val plan = frontier.plan(
            UltraFrontierRequest(
                message = request.originalText,
                query = request,
                networkAvailable = networkAvailable()
            )
        )

        if (plan.lane == UltraFrontierLane.BLOCKED) {
            return UltraQueryExecutionAnswer(
                message = requireNotNull(plan.blockedReason),
                verified = false,
                abstained = true,
                reasonCode = "FRONTIER_NETWORK_REQUIRED",
                retryable = true,
                stage = "frontier"
            )
        }

        var attempt = 1
        var answer = coordinator.answer(
            request = request,
            localChat = localChat
        )

        while (true) {
            when (
                critic.review(
                    plan = plan,
                    candidate = answer.toFrontierCandidate(attempt)
                )
            ) {
                UltraFrontierVerdict.ACCEPT ->
                    return answer

                UltraFrontierVerdict.FALLBACK_LOCAL -> {
                    val local = safeLocal(localChat)
                    if (local != null) {
                        return UltraQueryExecutionAnswer(
                            message = local,
                            verified = false,
                            fallbackUsed = true,
                            abstained = false,
                            reasonCode = "FRONTIER_LOCAL_FALLBACK",
                            stage = "frontier"
                        )
                    }
                    return answer
                }

                UltraFrontierVerdict.RETRY_RESEARCH -> {
                    if (attempt >= plan.researchPassBudget) {
                        return answer.asSafeAbstention()
                    }
                    attempt += 1
                    answer = coordinator.answer(
                        request = request,
                        localChat = localChat
                    )
                }

                UltraFrontierVerdict.ABSTAIN ->
                    return answer.asSafeAbstention()
            }
        }
    }

    private fun safeLocal(localChat: () -> String?): String? =
        try {
            localChat()
                ?.trim()
                ?.takeIf(String::isNotBlank)
        } catch (_: Exception) {
            null
        }

    private fun UltraQueryExecutionAnswer.toFrontierCandidate(
        attempt: Int
    ): UltraFrontierCandidate =
        UltraFrontierCandidate(
            message = message,
            verified = verified,
            confidence = confidence,
            sources = sources,
            abstained = abstained,
            attempt = attempt
        )

    private fun UltraQueryExecutionAnswer.asSafeAbstention(): UltraQueryExecutionAnswer =
        if (abstained) {
            copy(verified = false)
        } else {
            copy(
                verified = false,
                abstained = true,
                reasonCode = reasonCode ?: "FRONTIER_EVIDENCE_INSUFFICIENT",
                stage = stage ?: "frontier"
            )
        }
}
