package com.cardenaspiero255.gamehubultra.ai

/**
 * Executes a planned Frontier query using the existing structured query coordinator.
 *
 * The engine can retry research when observable evidence is weak, but it never converts
 * stale/local text into a verified answer for a fresh-data request.
 */
private data class UltraFrontierRetryProgress(
    val verified: Boolean,
    val confidenceRank: Int,
    val sources: Set<String>,
    val abstained: Boolean
) {
    fun improvesOn(previous: UltraFrontierRetryProgress): Boolean {
        val qualityScore =
            (if (verified) 1_000 else 0) +
                confidenceRank * 100 +
                sources.size * 10 +
                (if (!abstained) 1 else 0)
        val previousScore =
            (if (previous.verified) 1_000 else 0) +
                previous.confidenceRank * 100 +
                previous.sources.size * 10 +
                (if (!previous.abstained) 1 else 0)

        return qualityScore > previousScore ||
            sources.any { it !in previous.sources }
    }
}

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
        val initialNetworkAvailable = networkAvailable()
        val plan = frontier.plan(
            UltraFrontierRequest(
                message = request.originalText,
                query = request,
                networkAvailable = initialNetworkAvailable
            )
        )
        auditTrail.record(
            correlationId = request.correlationId,
            lane = plan.lane,
            event = UltraFrontierAuditEvent.PLAN_CREATED
        )

        if (plan.lane == UltraFrontierLane.BLOCKED) {
            auditTrail.record(
                correlationId = request.correlationId,
                lane = plan.lane,
                event = UltraFrontierAuditEvent.ABSTAIN,
                reasonCode = "FRONTIER_NETWORK_REQUIRED"
            )
            return networkAbstention(
                message = requireNotNull(plan.blockedReason),
                reasonCode = "FRONTIER_NETWORK_REQUIRED"
            )
        }

        var attempt = 1
        var previousRetryProgress: UltraFrontierRetryProgress? = null
        recordAttempt(request, plan, attempt)
        var answer = coordinator.answer(
            request = requestForAttempt(request, plan, attempt),
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
                        ?.takeIf(critic::isUsefulLocalAnswer)
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
                    if (
                        initialNetworkAvailable &&
                        request.verificationMode != UltraVerificationMode.REQUIRED
                    ) {
                        auditTrail.record(
                            correlationId = request.correlationId,
                            lane = plan.lane,
                            event = UltraFrontierAuditEvent.ESCALATE_RESEARCH,
                            attempt = attempt,
                            reasonCode = "FRONTIER_LOCAL_UNANSWERABLE"
                        )
                        return answer(
                            request = request.escalatedResearchRequest(),
                            localChat = { null }
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
                        return answer.asSafeAbstention("FRONTIER_BUDGET_EXHAUSTED")
                    }

                    val progress = answer.retryProgress()
                    val previousProgress = previousRetryProgress
                    if (
                        previousProgress != null &&
                        !progress.improvesOn(previousProgress)
                    ) {
                        auditTrail.record(
                            correlationId = request.correlationId,
                            lane = plan.lane,
                            event = UltraFrontierAuditEvent.ABSTAIN,
                            attempt = attempt,
                            reasonCode = "FRONTIER_NO_PROGRESS"
                        )
                        return answer.asSafeAbstention("FRONTIER_NO_PROGRESS")
                    }
                    previousRetryProgress = progress

                    if (isResearchLane(plan) && !networkAvailable()) {
                        auditTrail.record(
                            correlationId = request.correlationId,
                            lane = plan.lane,
                            event = UltraFrontierAuditEvent.ABSTAIN,
                            attempt = attempt,
                            reasonCode = "FRONTIER_NETWORK_LOST"
                        )
                        return networkAbstention(
                            message =
                                "Perdí la conexión antes de poder completar la verificación. " +
                                    "No voy a usar datos locales potencialmente desactualizados.",
                            reasonCode = "FRONTIER_NETWORK_LOST"
                        )
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
                        request = requestForAttempt(request, plan, attempt),
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

    private fun UltraGeneralQueryRequest.escalatedResearchRequest():
        UltraGeneralQueryRequest =
        copy(
            requiresInternet = true,
            timeoutMillis = maxOf(timeoutMillis, LOCAL_ESCALATION_TIMEOUT_MS),
            verificationMode = UltraVerificationMode.REQUIRED,
            researchProviderBudget = null
        )

    private fun requestForAttempt(
        request: UltraGeneralQueryRequest,
        plan: UltraFrontierPlan,
        attempt: Int
    ): UltraGeneralQueryRequest {
        val budget = if (plan.sourceBudget > 0) {
            (
                plan.sourceBudget +
                    ((attempt - 1) * plan.sourceBudgetStep)
                ).coerceAtMost(plan.maxSourceBudget)
        } else {
            0
        }
        return request.copy(
            researchProviderBudget = budget.takeIf { it > 0 }
        )
    }

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

    private fun isResearchLane(plan: UltraFrontierPlan): Boolean =
        plan.lane == UltraFrontierLane.VERIFIED_RESEARCH ||
            plan.lane == UltraFrontierLane.DEEP_RESEARCH

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
            retryable = abstained && retryable,
            attempt = attempt
        )

    private fun UltraQueryExecutionAnswer.retryProgress():
        UltraFrontierRetryProgress =
        UltraFrontierRetryProgress(
            verified = verified,
            confidenceRank = when (confidence) {
                UltraAnswerConfidence.HIGH -> 3
                UltraAnswerConfidence.MEDIUM -> 2
                UltraAnswerConfidence.LOW -> 1
                null -> 0
            },
            sources = sources
                .asSequence()
                .map(String::trim)
                .filter(String::isNotBlank)
                .map(String::lowercase)
                .toSet(),
            abstained = abstained
        )

    private fun networkAbstention(
        message: String,
        reasonCode: String
    ): UltraQueryExecutionAnswer =
        UltraQueryExecutionAnswer(
            message = message,
            verified = false,
            sources = emptyList(),
            abstained = true,
            reasonCode = reasonCode,
            retryable = true,
            stage = "frontier"
        )

    private fun UltraQueryExecutionAnswer.asSafeAbstention(
        overrideReasonCode: String? = null
    ): UltraQueryExecutionAnswer =
        if (abstained) {
            copy(
                verified = false,
                retryable = false,
                reasonCode = overrideReasonCode ?: reasonCode,
                stage = stage ?: "frontier"
            )
        } else {
            copy(
                message =
                    "Encontré información, pero no pude corroborarla con suficiente " +
                        "evidencia fiable. Prefiero no presentarla como un hecho.",
                verified = false,
                sources = emptyList(),
                abstained = true,
                retryable = false,
                reasonCode =
                    overrideReasonCode ?: reasonCode ?: "FRONTIER_EVIDENCE_INSUFFICIENT",
                stage = stage ?: "frontier"
            )
        }

    private companion object {
        const val LOCAL_ESCALATION_TIMEOUT_MS = 60_000L
    }
}
