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
    private val v2HierarchicalPlanner: UltraFrontierV2HierarchicalPlanner =
        UltraFrontierV2HierarchicalPlanner(),
    private val v2BranchAllocator: UltraFrontierV2BranchAllocator =
        UltraFrontierV2BranchAllocator(),
    private val v2Replanner: UltraFrontierV2Replanner =
        UltraFrontierV2Replanner(),
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
        val remainingMillis = remainingResearchMillis(
            plan = plan,
            executionStartedNanos = executionStartedNanos
        ).coerceAtLeast(1L)
        val baseResearchTaskIds = plan.tasks
            .filter { it.specialist == UltraFrontierSpecialist.RESEARCH }
            .mapTo(linkedSetOf()) { it.id }
        val replanned = if (attempt > 1 && baseResearchTaskIds.isNotEmpty()) {
            v2Replanner.replan(
                tasks = plan.tasks,
                failedTaskIds = baseResearchTaskIds,
                remainingTimeMillis = remainingMillis,
                recoveryOrdinal = attempt
            )
        } else {
            UltraFrontierV2ReplanResult(
                tasks = plan.tasks,
                changed = false,
                replacedTaskIds = emptySet()
            )
        }
        if (replanned.changed) {
            auditTrail.record(
                correlationId = request.correlationId,
                lane = plan.lane,
                event = UltraFrontierAuditEvent.REPLAN,
                attempt = attempt,
                reasonCode = "FRONTIER_V2_RESEARCH_RECOVERY"
            )
        }

        val v2Request = UltraFrontierRequest(
            message = request.originalText,
            query = request,
            networkAvailable = true
        )
        val hierarchy = v2HierarchicalPlanner.plan(
            request = v2Request,
            lane = plan.lane,
            tasks = replanned.tasks
        )
        val evidenceTaskIds = hierarchy.phases
            .firstOrNull { it.phase == UltraFrontierV2Phase.EVIDENCE }
            ?.taskIds
            .orEmpty()
        val researchTasks = replanned.tasks
            .filter {
                it.specialist == UltraFrontierSpecialist.RESEARCH &&
                    (evidenceTaskIds.isEmpty() || it.id in evidenceTaskIds)
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

        val latencies = java.util.concurrent.ConcurrentHashMap<String, Long>()
        val capacity = coordinator.providerPartitionCapacity
            ?.takeIf { it > 0 }
        val maxParallelism = evolution
            .budget(request, frontier.policy)
            .maxParallelism
            .coerceIn(1, researchTasks.size)
            .let { if (capacity != null) it.coerceAtMost(capacity) else it }
        val totalBudget =
            attemptRequest.researchProviderBudget
                ?: plan.sourceBudget.coerceAtLeast(1)
        val allocation = v2BranchAllocator.allocate(
            totalSourceBudget = totalBudget,
            maxParallelism = maxParallelism,
            signals = researchTasks.map { task ->
                UltraFrontierV2BranchSignal(
                    taskId = task.id,
                    reliability = if (task.mandatory) 0.75 else 0.55,
                    novelty = 0.80,
                    urgency = if (plan.requiresFreshResearch) 0.90 else 0.60
                )
            }
        )
        val allocatedTasks = researchTasks.filter {
            allocation.containsKey(it.id)
        }
        // Each attempt must explore a fresh provider partition, including
        // a device-pressure fallback where only one worker can execute.
        val partitionStride = if (capacity != null) {
            // A source budget measures desired evidence, not provider slots.
            // Walk one real slot per retry rather than skipping all providers.
            1
        } else {
            maxOf(plan.maxSourceBudget, totalBudget, 1)
        }
        val candidateOffset =
            request.researchProviderOffset +
                (attempt - 1) * partitionStride
        val attemptProviderOffset =
            if (capacity != null) Math.floorMod(candidateOffset, capacity)
            else candidateOffset
        if (allocatedTasks.size < 2) {
            return coordinator.answer(
                request = attemptRequest.copy(
                    researchProviderOffset = attemptProviderOffset
                ),
                localChat = localChat
            )
        }

        val providerOffsets = linkedMapOf<String, Int>()
        var nextProviderOffset = attemptProviderOffset
        allocatedTasks.forEach { task ->
            providerOffsets[task.id] = if (capacity != null) {
                Math.floorMod(nextProviderOffset, capacity)
            } else {
                nextProviderOffset
            }
            nextProviderOffset += if (capacity != null) 1
                else allocation.getValue(task.id)
        }

        val results = try {
            specialistExecutor.execute(
                tasks = allocatedTasks,
                maxParallelism = allocatedTasks.size.coerceIn(1, maxParallelism),
                timeoutMillis = remainingMillis
            ) { task ->
                val branchBudget = if (capacity != null) 1
                    else allocation.getValue(task.id)
                val branchRequest = attemptRequest.copy(
                    researchProviderBudget = branchBudget,
                    researchProviderOffset =
                        providerOffsets.getValue(task.id)
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

        val candidates = allocatedTasks.mapNotNull { task ->
            results[task.id]?.let { answer ->
                answer to (latencies[task.id] ?: 0L)
            }
        }
        return evolution.synthesizeResearch(candidates)
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
