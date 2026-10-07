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
    private val networkAvailable: () -> Boolean = { true },
    private val auditTrail: UltraFrontierAuditTrail = UltraFrontierAuditTrail()
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
        auditTrail.record(
            correlationId = request.correlationId,
            lane = plan.lane,
            event = UltraFrontierAuditEvent.PLAN_CREATED
        )
        val executionRequest = request.copy(
            researchProviderBudget = plan.sourceBudget.takeIf { it > 0 }
        )

        if (plan.lane == UltraFrontierLane.BLOCKED) {
            auditTrail.record(
                correlationId = request.correlationId,
                lane = plan.lane,
                event = UltraFrontierAuditEvent.ABSTAIN,
                reasonCode = "FRONTIER_NETWORK_REQUIRED"
            )
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
        recordAttempt(request, plan, attempt)
        var answer = coordinator.answer(
            request = executionRequest,
            localChat = localChat
        )

        while (true) {
            val verdict = critic.review(
                plan = plan,
                candidate = answer.toFrontierCandidate(attempt)
            )
            when (verdict) {
                UltraFrontierVerdict.ACCEPT -> {
                    auditTrail.record(
                        correlationId = request.correlationId,
                        lane = plan.lane,
                        event = UltraFrontierAuditEvent.ACCEPT,
                        attempt = attempt,
                        reasonCode = answer.reasonCode ?: if (answer.verified) {
                            "VERIFIED"
                        } else {
                            "LOCAL"
                        }
                    )
                    return answer
                }

                UltraFrontierVerdict.FALLBACK_LOCAL -> {
                    val local = safeLocal(localChat)
                    if (local != null) {
                        auditTrail.record(
                            correlationId = request.correlationId,
                            lane = plan.lane,
                            event = UltraFrontierAuditEvent.FALLBACK_LOCAL,
                            attempt = attempt,
                            reasonCode = "FRONTIER_LOCAL_FALLBACK"
                        )
                        return UltraQueryExecutionAnswer(
                            message = local,
                            verified = false,
                            fallbackUsed = true,
                            abstained = false,
                            reasonCode = "FRONTIER_LOCAL_FALLBACK",
                            stage = "frontier"
                        )
                    }
                    auditTrail.record(
                        correlationId = request.correlationId,
                        lane = plan.lane,
                        event = UltraFrontierAuditEvent.ABSTAIN,
                        attempt = attempt,
                        reasonCode = answer.reasonCode ?: "LOCAL_UNAVAILABLE"
                    )
                    return answer.asSafeAbstention()
                }

                UltraFrontierVerdict.RETRY_RESEARCH -> {
                    if (attempt >= plan.researchPassBudget) {
                        auditTrail.record(
                            correlationId = request.correlationId,
                            lane = plan.lane,
                            event = UltraFrontierAuditEvent.ABSTAIN,
                            attempt = attempt,
                            reasonCode = answer.reasonCode ?: "FRONTIER_BUDGET_EXHAUSTED"
                        )
                        return answer.asSafeAbstention()
                    }
                    auditTrail.record(
                        correlationId = request.correlationId,
                        lane = plan.lane,
                        event = UltraFrontierAuditEvent.RETRY,
                        attempt = attempt + 1,
                        reasonCode = answer.reasonCode ?: "EVIDENCE_INSUFFICIENT"
                    )
                    attempt += 1
                    recordAttempt(request, plan, attempt)
                    answer = coordinator.answer(
                        request = executionRequest,
                        localChat = localChat
                    )
                }

                UltraFrontierVerdict.ABSTAIN -> {
                    auditTrail.record(
                        correlationId = request.correlationId,
                        lane = plan.lane,
                        event = UltraFrontierAuditEvent.ABSTAIN,
                        attempt = attempt,
                        reasonCode = answer.reasonCode ?: "FRONTIER_EVIDENCE_INSUFFICIENT"
                    )
                    return answer.asSafeAbstention()
                }
            }
        }
    }

    fun auditSnapshot(): List<UltraFrontierAuditRecord> = auditTrail.snapshot()

    private fun recordAttempt(
        request: UltraGeneralQueryRequest,
        plan: UltraFrontierPlan,
        attempt: Int
    ) {
        val event = when (plan.lane) {
            UltraFrontierLane.VERIFIED_RESEARCH,
            UltraFrontierLane.DEEP_RESEARCH ->
                UltraFrontierAuditEvent.RESEARCH_ATTEMPT
            else ->
                UltraFrontierAuditEvent.LOCAL_ATTEMPT
        }
        auditTrail.record(
            correlationId = request.correlationId,
            lane = plan.lane,
            event = event,
            attempt = attempt
        )
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
