package com.cardenaspiero255.gamehubultra.ai

import java.util.concurrent.TimeUnit

/**
 * Executes a planned Frontier query using the existing structured query coordinator.
 *
 * The engine can retry research when observable evidence is weak, but it never converts
 * stale/local text into a verified answer for a fresh-data request.
 */
private data class UltraFrontierRetryProgress(
    val verified: Boolean,
    val confidenceRank: Int,
    val independentSourceCount: Int,
    val sources: Set<String>,
    val abstained: Boolean
) {
    fun improvesOn(previous: UltraFrontierRetryProgress): Boolean {
        val qualityScore =
            (if (verified) 1_000 else 0) +
                confidenceRank * 100 +
                independentSourceCount * 20 +
                sources.size * 10 +
                (if (!abstained) 1 else 0)
        val previousScore =
            (if (previous.verified) 1_000 else 0) +
                previous.confidenceRank * 100 +
                previous.independentSourceCount * 20 +
                previous.sources.size * 10 +
                (if (!previous.abstained) 1 else 0)

        return qualityScore > previousScore ||
            sources.any { it !in previous.sources }
    }
}

class UltraFrontierExecutionEngine(
    private val coordinator: UltraQueryExecutionCoordinator,
    private val evolution: UltraFrontierEvolutionController =
        UltraFrontierEvolutionController(),
    private val frontier: UltraFrontierOrchestrator =
        UltraFrontierOrchestrator(evolution = evolution),
    private val critic: UltraFrontierCritic = UltraFrontierCritic(),
    private val specialistExecutor: UltraFrontierSpecialistExecutor =
        UltraFrontierSpecialistExecutor(),
    private val networkAvailable: () -> Boolean = { true },
    private val auditTrail: UltraFrontierAuditTrail = UltraFrontierAuditTrail(),
    private val nanoTime: () -> Long = System::nanoTime,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    fun answer(
        request: UltraGeneralQueryRequest,
        localChat: () -> String?
    ): UltraQueryExecutionAnswer {
        val executionStartedNanos = nanoTime()
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

        if (
            plan.lane == UltraFrontierLane.VERIFIED_RESEARCH ||
            plan.lane == UltraFrontierLane.DEEP_RESEARCH
        ) {
            evolution.recallVerified(
                request = request,
                nowMillis = nowMillis()
            )?.let { recalled ->
                auditTrail.record(
                    correlationId = request.correlationId,
                    lane = plan.lane,
                    event = UltraFrontierAuditEvent.ACCEPT,
                    reasonCode = "FRONTIER_TEMPORAL_KNOWLEDGE"
                )
                return complete(
                    request = request,
                    plan = plan,
                    answer = recalled,
                    executionStartedNanos = executionStartedNanos
                )
            }
        }

        if (plan.lane == UltraFrontierLane.BLOCKED) {
            auditTrail.record(
                correlationId = request.correlationId,
                lane = plan.lane,
                event = UltraFrontierAuditEvent.ABSTAIN,
                reasonCode = "FRONTIER_NETWORK_REQUIRED"
            )
            return complete(
                request = request,
                plan = plan,
                answer = networkAbstention(
                    message = requireNotNull(plan.blockedReason),
                    reasonCode = "FRONTIER_NETWORK_REQUIRED"
                ),
                executionStartedNanos = executionStartedNanos
            )
        }

        if (
            evolution.shouldRunEnsemble(
                request = request,
                lane = plan.lane,
                networkAvailable = initialNetworkAvailable
            )
        ) {
            adaptiveEnsembleAnswer(
                request = request,
                localPlan = plan,
                localChat = localChat,
                executionStartedNanos = executionStartedNanos
            )?.let { return it }
        }

        var attempt = 1
        var previousRetryProgress: UltraFrontierRetryProgress? = null
        recordAttempt(request, plan, attempt)
        var answer = executePlannedAttempt(
            request = request,
            plan = plan,
            attempt = attempt,
            executionStartedNanos = executionStartedNanos,
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
                    return complete(
                        request = request,
                        plan = plan,
                        answer = answer,
                        executionStartedNanos = executionStartedNanos
                    )
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
                        return complete(
                            request = request,
                            plan = plan,
                            answer = UltraQueryExecutionAnswer(
                                message = local,
                                verified = false,
                                fallbackUsed = true,
                                abstained = false,
                                reasonCode = "FRONTIER_LOCAL_FALLBACK",
                                stage = "frontier"
                            ),
                            executionStartedNanos = executionStartedNanos
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
                        recordEvolutionOutcome(
                            request = request,
                            plan = plan,
                            answer = answer.asSafeAbstention(
                                "FRONTIER_LOCAL_UNANSWERABLE"
                            ),
                            executionStartedNanos = executionStartedNanos
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
                    return complete(
                        request = request,
                        plan = plan,
                        answer = answer.asSafeAbstention(),
                        executionStartedNanos = executionStartedNanos
                    )
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
                        return complete(
                            request = request,
                            plan = plan,
                            answer = answer.asSafeAbstention(
                                "FRONTIER_BUDGET_EXHAUSTED"
                            ),
                            executionStartedNanos = executionStartedNanos
                        )
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
                        return complete(
                            request = request,
                            plan = plan,
                            answer = answer.asSafeAbstention(
                                "FRONTIER_NO_PROGRESS"
                            ),
                            executionStartedNanos = executionStartedNanos
                        )
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
                        return complete(
                            request = request,
                            plan = plan,
                            answer = networkAbstention(
                                message =
                                    "Perdí la conexión antes de poder completar la verificación. " +
                                        "No voy a usar datos locales potencialmente desactualizados.",
                                reasonCode = "FRONTIER_NETWORK_LOST"
                            ),
                            executionStartedNanos = executionStartedNanos
                        )
                    }

                    if (
                        isResearchLane(plan) &&
                        remainingResearchMillis(
                            plan = plan,
                            executionStartedNanos = executionStartedNanos
                        ) <= 0L
                    ) {
                        auditTrail.record(
                            correlationId = request.correlationId,
                            lane = plan.lane,
                            event = UltraFrontierAuditEvent.ABSTAIN,
                            attempt = attempt,
                            reasonCode = "FRONTIER_TIME_BUDGET_EXHAUSTED"
                        )
                        return complete(
                            request = request,
                            plan = plan,
                            answer = answer.asSafeAbstention(
                                "FRONTIER_TIME_BUDGET_EXHAUSTED"
                            ),
                            executionStartedNanos = executionStartedNanos
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
                    answer = executePlannedAttempt(
                        request = request,
                        plan = plan,
                        attempt = attempt,
                        executionStartedNanos = executionStartedNanos,
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
                    return complete(
                        request = request,
                        plan = plan,
                        answer = answer.asSafeAbstention(),
                        executionStartedNanos = executionStartedNanos
                    )
                }
            }
        }
    }

    fun auditSnapshot(): List<UltraFrontierAuditRecord> = auditTrail.snapshot()

    private fun deepTaskGraphAnswer(
        request: UltraGeneralQueryRequest,
        plan: UltraFrontierPlan,
        executionStartedNanos: Long
    ): UltraQueryExecutionAnswer? {
        val plannedResearchTasks = plan.tasks.filter {
            it.specialist == UltraFrontierSpecialist.RESEARCH
        }
        if (plannedResearchTasks.size < 2) return null

        val executableTasks = plannedResearchTasks.map {
            it.copy(dependsOn = emptySet())
        }
        val latencies =
            java.util.concurrent.ConcurrentHashMap<String, Long>()
        val remainingMillis = remainingResearchMillis(
            plan = plan,
            executionStartedNanos = executionStartedNanos
        )
        if (remainingMillis <= 0L) return null

        val results = try {
            specialistExecutor.execute(
                tasks = executableTasks,
                maxParallelism = executableTasks.size.coerceIn(1, 4),
                timeoutMillis = remainingMillis.coerceAtLeast(1L)
            ) { task ->
                val branchIndex = executableTasks.indexOfFirst {
                    it.id == task.id
                }.coerceAtLeast(0)
                val branchBudget = (
                    plan.sourceBudget + executableTasks.size - 1
                    ) / executableTasks.size
                val branchRequest = requestForAttempt(
                    request = request,
                    plan = plan,
                    attempt = 1,
                    executionStartedNanos = executionStartedNanos
                ).copy(
                    researchProviderBudget = branchBudget.coerceAtLeast(1),
                    researchProviderOffset =
                        request.researchProviderOffset +
                            branchIndex * branchBudget.coerceAtLeast(1)
                )
                val started = nanoTime()
                val result = coordinator.answer(
                    request = branchRequest,
                    localChat = { null }
                )
                latencies[task.id] = TimeUnit.NANOSECONDS.toMillis(
                    (nanoTime() - started).coerceAtLeast(0L)
                )
                result
            }
        } catch (_: Exception) {
            return null
        }

        val accepted = executableTasks.mapNotNull { task ->
            val answer = results[task.id] ?: return@mapNotNull null
            val verdict = critic.review(
                plan = plan,
                candidate = answer.toFrontierCandidate(attempt = 1)
            )
            if (verdict != UltraFrontierVerdict.ACCEPT) {
                return@mapNotNull null
            }
            answer to (latencies[task.id] ?: 0L)
        }
        if (accepted.isEmpty()) return null

        val selected = evolution.synthesizeResearch(accepted)
            ?: return null

        auditTrail.record(
            correlationId = request.correlationId,
            lane = plan.lane,
            event = UltraFrontierAuditEvent.ENSEMBLE_COMPARE,
            attempt = 1,
            reasonCode = "FRONTIER_DEEP_TASK_GRAPH"
        )
        accepted
            .asSequence()
            .map { it.first }
            .filter { it != selected }
            .forEach { unselected ->
                recordEvolutionOutcome(
                    request = request,
                    plan = plan,
                    answer = unselected,
                    executionStartedNanos = executionStartedNanos
                )
            }

        return complete(
            request = request,
            plan = plan,
            answer = selected,
            executionStartedNanos = executionStartedNanos
        )
    }

    private fun adaptiveEnsembleAnswer(
        request: UltraGeneralQueryRequest,
        localPlan: UltraFrontierPlan,
        localChat: () -> String?,
        executionStartedNanos: Long
    ): UltraQueryExecutionAnswer? {
        val researchRequest = request
            .escalatedResearchRequest()
            .copy(
                timeoutMillis = minOf(
                    request.timeoutMillis.coerceAtLeast(1L),
                    ENSEMBLE_RESEARCH_TIMEOUT_MS
                )
            )
        val researchPlan = frontier.plan(
            UltraFrontierRequest(
                message = researchRequest.originalText,
                query = researchRequest,
                networkAvailable = true
            )
        )
        if (!isResearchLane(researchPlan)) return null

        val localTask = UltraFrontierTask(
            id = "ensemble-local",
            specialist = UltraFrontierSpecialist.LOCAL_REASONER,
            parallelGroup = "ensemble"
        )
        val researchTask = UltraFrontierTask(
            id = "ensemble-research",
            specialist = UltraFrontierSpecialist.RESEARCH,
            parallelGroup = "ensemble"
        )
        val latencies = java.util.concurrent.ConcurrentHashMap<String, Long>()

        val results = try {
            specialistExecutor.execute(
                tasks = listOf(localTask, researchTask),
                maxParallelism = 2,
                timeoutMillis = maxOf(
                    request.timeoutMillis.coerceAtLeast(1L),
                    ENSEMBLE_EXECUTION_FLOOR_MS
                )
            ) { task ->
                val started = nanoTime()
                val result = when (task.id) {
                    localTask.id -> coordinator.answer(
                        request = request,
                        localChat = localChat
                    )
                    researchTask.id -> coordinator.answer(
                        request = researchRequest,
                        localChat = { null }
                    )
                    else -> error("Unknown Frontier ensemble task: ${task.id}")
                }
                latencies[task.id] = TimeUnit.NANOSECONDS.toMillis(
                    (nanoTime() - started).coerceAtLeast(0L)
                )
                result
            }
        } catch (_: Exception) {
            return null
        }

        val local = results[localTask.id]
        val research = results[researchTask.id]
        val selected = evolution.selectEnsemble(
            local = local,
            research = research,
            localLatencyMillis = latencies[localTask.id] ?: 0L,
            researchLatencyMillis = latencies[researchTask.id] ?: 0L,
            requiresFreshData = request.requiresFreshData
        ) ?: return null

        auditTrail.record(
            correlationId = request.correlationId,
            lane = localPlan.lane,
            event = UltraFrontierAuditEvent.ENSEMBLE_COMPARE,
            reasonCode = if (selected == research) {
                "FRONTIER_ENSEMBLE_RESEARCH"
            } else {
                "FRONTIER_ENSEMBLE_LOCAL"
            }
        )

        if (local != null && selected != local) {
            recordEvolutionOutcome(
                request = request,
                plan = localPlan,
                answer = local,
                executionStartedNanos = executionStartedNanos
            )
        }
        if (research != null && selected != research) {
            recordEvolutionOutcome(
                request = researchRequest,
                plan = researchPlan,
                answer = research,
                executionStartedNanos = executionStartedNanos
            )
        }

        return if (selected == research) {
            complete(
                request = researchRequest,
                plan = researchPlan,
                answer = selected,
                executionStartedNanos = executionStartedNanos
            )
        } else {
            complete(
                request = request,
                plan = localPlan,
                answer = selected,
                executionStartedNanos = executionStartedNanos
            )
        }
    }

    private fun complete(
        request: UltraGeneralQueryRequest,
        plan: UltraFrontierPlan,
        answer: UltraQueryExecutionAnswer,
        executionStartedNanos: Long
    ): UltraQueryExecutionAnswer {
        val completedAtMillis = nowMillis()
        val gatedAnswer = evolution.finalGate(
            request = request,
            plan = plan,
            answer = answer,
            nowMillis = completedAtMillis
        )
        recordEvolutionOutcome(
            request = request,
            plan = plan,
            answer = gatedAnswer,
            executionStartedNanos = executionStartedNanos
        )
        evolution.rememberVerified(
            request = request,
            result = gatedAnswer,
            nowMillis = completedAtMillis
        )
        return gatedAnswer
    }

    private fun recordEvolutionOutcome(
        request: UltraGeneralQueryRequest,
        plan: UltraFrontierPlan,
        answer: UltraQueryExecutionAnswer,
        executionStartedNanos: Long
    ) {
        val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(
            (nanoTime() - executionStartedNanos).coerceAtLeast(0L)
        )
        evolution.record(
            UltraFrontierExecutionOutcome(
                domain = evolution.domain(request),
                lane = plan.lane,
                accepted = !answer.abstained,
                verified = answer.verified,
                abstained = answer.abstained,
                latencyMillis = elapsedMillis,
                reasonCode = answer.reasonCode
            )
        )
    }

    private fun UltraGeneralQueryRequest.escalatedResearchRequest():
        UltraGeneralQueryRequest =
        copy(
            requiresInternet = true,
            timeoutMillis = maxOf(timeoutMillis, LOCAL_ESCALATION_TIMEOUT_MS),
            verificationMode = UltraVerificationMode.REQUIRED,
            researchProviderBudget = null
        )

    private fun executePlannedAttempt(
        request: UltraGeneralQueryRequest,
        plan: UltraFrontierPlan,
        attempt: Int,
        executionStartedNanos: Long,
        localChat: () -> String?
    ): UltraQueryExecutionAnswer {
        val attemptRequest = requestForAttempt(
            request = request,
            plan = plan,
            attempt = attempt,
            executionStartedNanos = executionStartedNanos
        )
        val researchTasks = plan.tasks
            .filter {
                it.specialist == UltraFrontierSpecialist.RESEARCH
            }
            .map {
                // Routing/context prerequisites have already been resolved by
                // the orchestrator before this research subgraph starts.
                it.copy(dependsOn = emptySet())
            }
        if (
            plan.lane != UltraFrontierLane.DEEP_RESEARCH ||
            researchTasks.size < 2 ||
            !coordinator.supportsProviderPartitioning
        ) {
            return coordinator.answer(
                request = attemptRequest,
                localChat = localChat
            )
        }

        val taskIndexes = researchTasks
            .mapIndexed { index, task -> task.id to index }
            .toMap()
        val latencies = java.util.concurrent.ConcurrentHashMap<String, Long>()
        val remainingMillis = remainingResearchMillis(
            plan = plan,
            executionStartedNanos = executionStartedNanos
        ).coerceAtLeast(1L)
        val maxParallelism = evolution
            .budget(request, frontier.policy)
            .maxParallelism
            .coerceIn(1, researchTasks.size)

        val results = try {
            specialistExecutor.execute(
                tasks = researchTasks,
                maxParallelism = maxParallelism,
                timeoutMillis = remainingMillis
            ) { task ->
                val branchIndex = taskIndexes.getValue(task.id)
                val branchRequest = attemptRequest.copy(
                    researchProviderOffset = branchIndex
                )
                val started = nanoTime()
                val result = coordinator.answer(
                    request = branchRequest,
                    localChat = { null }
                )
                latencies[task.id] = TimeUnit.NANOSECONDS.toMillis(
                    (nanoTime() - started).coerceAtLeast(0L)
                )
                result
            }
        } catch (_: Exception) {
            return coordinator.answer(
                request = attemptRequest,
                localChat = localChat
            )
        }

        val candidates = researchTasks.mapNotNull { task ->
            results[task.id]?.let { answer ->
                answer to (latencies[task.id] ?: 0L)
            }
        }
        return evolution.selectResearchEnsemble(candidates)
            ?: coordinator.answer(
                request = attemptRequest,
                localChat = localChat
            )
    }

    private fun requestForAttempt(
        request: UltraGeneralQueryRequest,
        plan: UltraFrontierPlan,
        attempt: Int,
        executionStartedNanos: Long
    ): UltraGeneralQueryRequest {
        val budget = if (plan.sourceBudget > 0) {
            (
                plan.sourceBudget +
                    ((attempt - 1) * plan.sourceBudgetStep)
                ).coerceAtMost(plan.maxSourceBudget)
        } else {
            0
        }
        val attemptTimeoutMillis = if (isResearchLane(plan)) {
            remainingResearchMillis(
                plan = plan,
                executionStartedNanos = executionStartedNanos
            )
                .coerceAtLeast(1L)
                .coerceAtMost(request.timeoutMillis)
        } else {
            request.timeoutMillis
        }
        return request.copy(
            timeoutMillis = attemptTimeoutMillis,
            researchProviderBudget = budget.takeIf { it > 0 }
        )
    }

    private fun remainingResearchMillis(
        plan: UltraFrontierPlan,
        executionStartedNanos: Long
    ): Long {
        if (!isResearchLane(plan) || plan.researchTimeBudgetMillis <= 0L) {
            return Long.MAX_VALUE
        }
        val elapsedNanos =
            (nanoTime() - executionStartedNanos).coerceAtLeast(0L)
        val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(elapsedNanos)
        return (plan.researchTimeBudgetMillis - elapsedMillis)
            .coerceAtLeast(0L)
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
            independentSourceCount = independentSourceCount,
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
            independentSourceCount = independentSourceCount.coerceAtLeast(0),
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
        const val ENSEMBLE_RESEARCH_TIMEOUT_MS = 20_000L
        const val ENSEMBLE_EXECUTION_FLOOR_MS = 25_000L
    }
}
