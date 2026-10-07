package com.cardenaspiero255.gamehubultra.ai

import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

enum class UltraFrontierDomain {
    GENERAL_KNOWLEDGE,
    CURRENT_DATA,
    COMPARISON,
    TECHNICAL,
    STUDY,
    GAMING
}

object UltraFrontierDomainClassifier {
    private val technicalPattern = Regex(
        """\b(android|gradle|kotlin|java|python|javascript|typescript|sql|api|github|git|codigo|código|programacion|programación|error|crash|build)\b"""
    )
    private val studyPattern = Regex(
        """\b(matematica|matemáticas|matematicas|fisica|física|quimica|química|biologia|biología|historia|geografia|geografía|estudiar|ejercicio|ecuacion|ecuación)\b"""
    )
    private val gamingPattern = Regex(
        """\b(juego|gaming|fps|latencia|ping|perfil|x4|router gaming|gamehub|gpu|thermal|termico|térmico|bateria|batería)\b"""
    )

    fun classify(request: UltraGeneralQueryRequest): UltraFrontierDomain =
        when (request.kind) {
            UltraGeneralQueryKind.CURRENT_DATA ->
                UltraFrontierDomain.CURRENT_DATA
            UltraGeneralQueryKind.COMPARISON_RESEARCH ->
                UltraFrontierDomain.COMPARISON
            UltraGeneralQueryKind.GENERAL_KNOWLEDGE -> {
                val normalized = request.originalText
                    .lowercase(Locale.ROOT)
                when {
                    technicalPattern.containsMatchIn(normalized) ->
                        UltraFrontierDomain.TECHNICAL
                    studyPattern.containsMatchIn(normalized) ->
                        UltraFrontierDomain.STUDY
                    gamingPattern.containsMatchIn(normalized) ->
                        UltraFrontierDomain.GAMING
                    else ->
                        UltraFrontierDomain.GENERAL_KNOWLEDGE
                }
            }
        }
}

data class UltraFrontierExecutionOutcome(
    val domain: UltraFrontierDomain,
    val lane: UltraFrontierLane,
    val accepted: Boolean,
    val verified: Boolean,
    val abstained: Boolean,
    val latencyMillis: Long,
    val reasonCode: String?
)

class UltraFrontierLearningStore(
    private val capacity: Int = 256,
    private val minSamplesForPreference: Int = 3
) {
    private val outcomes = ArrayDeque<UltraFrontierExecutionOutcome>()

    init {
        require(capacity >= 8)
        require(minSamplesForPreference >= 1)
    }

    @Synchronized
    fun record(outcome: UltraFrontierExecutionOutcome) {
        while (outcomes.size >= capacity) {
            outcomes.removeFirst()
        }
        outcomes.addLast(
            outcome.copy(latencyMillis = outcome.latencyMillis.coerceAtLeast(0L))
        )
    }

    @Synchronized
    fun preferredLane(
        domain: UltraFrontierDomain,
        candidates: Set<UltraFrontierLane>
    ): UltraFrontierLane? {
        if (candidates.isEmpty()) return null
        val grouped = outcomes
            .filter { it.domain == domain && it.lane in candidates }
            .groupBy { it.lane }
            .filterValues { it.size >= minSamplesForPreference }
        if (grouped.isEmpty()) return null

        return grouped.maxByOrNull { (_, samples) ->
            val accepted = samples.count { it.accepted }
            val verified = samples.count { it.verified }
            val abstained = samples.count { it.abstained }
            val averageLatency = samples
                .map { it.latencyMillis }
                .average()
            accepted * 400.0 +
                verified * 250.0 -
                abstained * 300.0 -
                averageLatency / 25.0
        }?.key
    }

    @Synchronized
    fun failurePressure(domain: UltraFrontierDomain): Int =
        outcomes
            .filter { it.domain == domain }
            .takeLast(12)
            .count { !it.accepted || it.abstained }
            .coerceAtMost(5)
}

class UltraAdaptiveProviderRanker {
    private data class Stats(
        var attempts: Int = 0,
        var successes: Int = 0,
        var failures: Int = 0,
        var authoritativeEvidence: Int = 0,
        var independentEvidence: Int = 0,
        var totalLatencyMillis: Long = 0L
    )

    private val stats = mutableMapOf<Pair<UltraFrontierDomain, String>, Stats>()

    @Synchronized
    fun record(
        providerId: String,
        domain: UltraFrontierDomain,
        result: UltraProviderResult,
        latencyMillis: Long
    ) {
        val id = providerId.trim()
        if (id.isBlank()) return
        val state = stats.getOrPut(domain to id) { Stats() }
        state.attempts += 1
        state.totalLatencyMillis += latencyMillis.coerceAtLeast(0L)
        when (result) {
            is UltraProviderResult.Evidence -> {
                state.successes += 1
                if (result.evidence.authoritative) {
                    state.authoritativeEvidence += 1
                }
                state.independentEvidence +=
                    result.evidence.independentSourceCount.coerceAtLeast(1)
            }
            is UltraProviderResult.Failure -> state.failures += 1
            is UltraProviderResult.Abstained -> {
                if (result.retryable) {
                    state.failures += 1
                }
            }
        }
    }

    @Synchronized
    fun score(
        providerId: String,
        domain: UltraFrontierDomain
    ): Double {
        val state = stats[domain to providerId.trim()] ?: return 0.50
        val reliability =
            (state.successes + 1.0) / (state.attempts + 2.0)
        val failurePenalty =
            state.failures.toDouble() / state.attempts.coerceAtLeast(1)
        val authorityBonus =
            state.authoritativeEvidence.toDouble() /
                state.attempts.coerceAtLeast(1) * 0.20
        val independenceBonus =
            (state.independentEvidence.toDouble() /
                state.attempts.coerceAtLeast(1))
                .coerceAtMost(3.0) * 0.05
        val averageLatency =
            state.totalLatencyMillis.toDouble() /
                state.attempts.coerceAtLeast(1)
        val latencyPenalty =
            (averageLatency / 10_000.0).coerceAtMost(0.25)

        return (
            reliability +
                authorityBonus +
                independenceBonus -
                failurePenalty * 0.45 -
                latencyPenalty
            ).coerceIn(0.0, 1.0)
    }

    fun rank(
        providers: List<UltraResearchProvider>,
        domain: UltraFrontierDomain
    ): List<UltraResearchProvider> =
        providers.withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<UltraResearchProvider>> {
                    score(it.value.id, domain)
                }.thenBy { it.index }
            )
            .map { it.value }
}

data class UltraWeightedEvidenceCandidate(
    val providerId: String,
    val claimKey: String,
    val value: String,
    val displayText: String,
    val sourceIds: Set<String>,
    val independentSourceCount: Int,
    val authoritative: Boolean,
    val providerScore: Double
)

data class UltraWeightedConsensusDecision(
    val accepted: Boolean,
    val claimKey: String?,
    val value: String?,
    val displayText: String?,
    val evidence: List<UltraWeightedEvidenceCandidate>,
    val independentSourceCount: Int,
    val confidence: UltraAnswerConfidence
)

class UltraWeightedConsensusEngine(
    private val minimumWinnerRatio: Double = 1.35,
    private val minimumIndependentSources: Int = 1
) {
    init {
        require(minimumWinnerRatio >= 1.0)
        require(minimumIndependentSources >= 1)
    }

    fun decide(
        candidates: List<UltraWeightedEvidenceCandidate>
    ): UltraWeightedConsensusDecision {
        if (candidates.isEmpty()) {
            return UltraWeightedConsensusDecision(
                accepted = false,
                claimKey = null,
                value = null,
                displayText = null,
                evidence = emptyList(),
                independentSourceCount = 0,
                confidence = UltraAnswerConfidence.LOW
            )
        }

        val groups = candidates.groupBy {
            normalize(it.claimKey) to normalize(it.value)
        }
        val ranked = groups.entries
            .map { entry ->
                val evidence = entry.value
                val weight = evidence.sumOf(::weight)
                Triple(entry.key, evidence, weight)
            }
            .sortedByDescending { it.third }

        val winner = ranked.first()
        val runnerWeight = ranked.getOrNull(1)?.third ?: 0.0
        val independentSources = winner.second.sumOf {
            it.independentSourceCount.coerceAtLeast(1)
        }
        val ratioSatisfied =
            runnerWeight <= 0.0 ||
                winner.third >= runnerWeight * minimumWinnerRatio
        val accepted =
            independentSources >= minimumIndependentSources &&
                ratioSatisfied
        val confidence = when {
            accepted && independentSources >= 2 ->
                UltraAnswerConfidence.HIGH
            independentSources >= 1 ->
                UltraAnswerConfidence.MEDIUM
            else ->
                UltraAnswerConfidence.LOW
        }
        val display = winner.second
            .maxByOrNull { weight(it) }
            ?.displayText

        return UltraWeightedConsensusDecision(
            accepted = accepted,
            claimKey = winner.first.first,
            value = winner.second.firstOrNull()?.value,
            displayText = display,
            evidence = winner.second,
            independentSourceCount = independentSources,
            confidence = confidence
        )
    }

    private fun weight(candidate: UltraWeightedEvidenceCandidate): Double =
        candidate.providerScore.coerceIn(0.0, 1.0) * 2.0 +
            candidate.independentSourceCount.coerceAtLeast(1)
                .coerceAtMost(4) * 1.5 +
            if (candidate.authoritative) 2.0 else 0.0

    private fun normalize(value: String): String =
        value.trim().lowercase(Locale.ROOT)
}

enum class UltraClaimStatus {
    VERIFIED,
    WEAK,
    STALE,
    INFERRED
}

data class UltraFrontierClaim(
    val id: String,
    val text: String,
    val confidence: UltraAnswerConfidence,
    val independentSourceCount: Int,
    val validUntilMillis: Long? = null,
    val inferred: Boolean = false
)

class UltraClaimVerifier {
    fun verify(
        claim: UltraFrontierClaim,
        minimumIndependentSources: Int,
        nowMillis: Long
    ): UltraClaimStatus {
        require(minimumIndependentSources >= 1)
        if (claim.inferred) return UltraClaimStatus.INFERRED
        if (
            claim.validUntilMillis != null &&
            nowMillis > claim.validUntilMillis
        ) {
            return UltraClaimStatus.STALE
        }
        if (
            claim.confidence == UltraAnswerConfidence.LOW ||
            claim.independentSourceCount < minimumIndependentSources
        ) {
            return UltraClaimStatus.WEAK
        }
        return UltraClaimStatus.VERIFIED
    }
}

data class UltraTemporalFact(
    val subject: String,
    val predicate: String,
    val value: String,
    val validFromMillis: Long,
    val validUntilMillis: Long? = null,
    val confidence: UltraAnswerConfidence,
    val sourceIds: Set<String> = emptySet()
)

class UltraTemporalKnowledgeGraph(
    private val capacity: Int = 256
) {
    private val facts = ArrayDeque<UltraTemporalFact>()

    init {
        require(capacity >= 8)
    }

    @Synchronized
    fun upsert(fact: UltraTemporalFact) {
        require(fact.subject.isNotBlank())
        require(fact.predicate.isNotBlank())
        require(
            fact.validUntilMillis == null ||
                fact.validUntilMillis >= fact.validFromMillis
        )
        facts.removeAll { existing ->
            existing.subject == fact.subject &&
                existing.predicate == fact.predicate &&
                existing.validFromMillis == fact.validFromMillis
        }
        while (facts.size >= capacity) {
            facts.removeFirst()
        }
        facts.addLast(fact)
    }

    @Synchronized
    fun resolve(
        subject: String,
        predicate: String,
        atMillis: Long
    ): UltraTemporalFact? =
        facts
            .asSequence()
            .filter {
                it.subject == subject &&
                    it.predicate == predicate &&
                    atMillis >= it.validFromMillis &&
                    (
                        it.validUntilMillis == null ||
                            atMillis <= it.validUntilMillis
                        )
            }
            .maxWithOrNull(
                compareBy<UltraTemporalFact> {
                    confidenceRank(it.confidence)
                }.thenBy { it.validFromMillis }
            )

    @Synchronized
    fun snapshot(): List<UltraTemporalFact> = facts.toList()

    private fun confidenceRank(confidence: UltraAnswerConfidence): Int =
        when (confidence) {
            UltraAnswerConfidence.HIGH -> 3
            UltraAnswerConfidence.MEDIUM -> 2
            UltraAnswerConfidence.LOW -> 1
        }
}

data class UltraFrontierTask(
    val id: String,
    val specialist: UltraFrontierSpecialist,
    val dependsOn: Set<String> = emptySet(),
    val parallelGroup: String? = null,
    val mandatory: Boolean = true
)

class UltraFrontierTaskPlanner {
    fun plan(
        request: UltraFrontierRequest,
        lane: UltraFrontierLane
    ): List<UltraFrontierTask> {
        val tasks = mutableListOf(
            UltraFrontierTask(
                id = "route",
                specialist = UltraFrontierSpecialist.ROUTER
            )
        )
        var contextDependencies = setOf("route")

        if (request.memoryAvailable && shouldUseContext(request.message)) {
            tasks += UltraFrontierTask(
                id = "memory",
                specialist = UltraFrontierSpecialist.MEMORY,
                dependsOn = setOf("route"),
                parallelGroup = "context",
                mandatory = false
            )
            contextDependencies += "memory"
        }
        if (request.telemetryAvailable) {
            tasks += UltraFrontierTask(
                id = "telemetry",
                specialist = UltraFrontierSpecialist.TELEMETRY,
                dependsOn = setOf("route"),
                parallelGroup = "context",
                mandatory = false
            )
            contextDependencies += "telemetry"
        }
        if (request.attachments.isNotEmpty()) {
            tasks += UltraFrontierTask(
                id = "multimodal",
                specialist = UltraFrontierSpecialist.MULTIMODAL,
                dependsOn = setOf("route"),
                parallelGroup = "context"
            )
            contextDependencies += "multimodal"
        }

        when (lane) {
            UltraFrontierLane.LOCAL_FAST,
            UltraFrontierLane.LOCAL_DELIBERATE -> {
                tasks += UltraFrontierTask(
                    id = "local",
                    specialist = UltraFrontierSpecialist.LOCAL_REASONER,
                    dependsOn = contextDependencies
                )
                tasks += UltraFrontierTask(
                    id = "critic",
                    specialist = UltraFrontierSpecialist.CRITIC,
                    dependsOn = setOf("local")
                )
            }

            UltraFrontierLane.VERIFIED_RESEARCH -> {
                tasks += UltraFrontierTask(
                    id = "research",
                    specialist = UltraFrontierSpecialist.RESEARCH,
                    dependsOn = contextDependencies
                )
                tasks += UltraFrontierTask(
                    id = "verify",
                    specialist = UltraFrontierSpecialist.VERIFIER,
                    dependsOn = setOf("research")
                )
                tasks += UltraFrontierTask(
                    id = "critic",
                    specialist = UltraFrontierSpecialist.CRITIC,
                    dependsOn = setOf("verify")
                )
            }

            UltraFrontierLane.DEEP_RESEARCH -> {
                tasks += UltraFrontierTask(
                    id = "research-a",
                    specialist = UltraFrontierSpecialist.RESEARCH,
                    dependsOn = contextDependencies,
                    parallelGroup = "evidence"
                )
                tasks += UltraFrontierTask(
                    id = "research-b",
                    specialist = UltraFrontierSpecialist.RESEARCH,
                    dependsOn = contextDependencies,
                    parallelGroup = "evidence"
                )
                tasks += UltraFrontierTask(
                    id = "synthesize",
                    specialist = UltraFrontierSpecialist.SYNTHESIZER,
                    dependsOn = setOf("research-a", "research-b")
                )
                tasks += UltraFrontierTask(
                    id = "verify",
                    specialist = UltraFrontierSpecialist.VERIFIER,
                    dependsOn = setOf("synthesize")
                )
                tasks += UltraFrontierTask(
                    id = "critic",
                    specialist = UltraFrontierSpecialist.CRITIC,
                    dependsOn = setOf("verify")
                )
            }

            UltraFrontierLane.TOOL_ACTION -> {
                tasks += UltraFrontierTask(
                    id = "safety",
                    specialist = UltraFrontierSpecialist.SAFETY_GATE,
                    dependsOn = contextDependencies
                )
                tasks += UltraFrontierTask(
                    id = "tool",
                    specialist = UltraFrontierSpecialist.TOOL_GATEWAY,
                    dependsOn = setOf("safety")
                )
                tasks += UltraFrontierTask(
                    id = "critic",
                    specialist = UltraFrontierSpecialist.CRITIC,
                    dependsOn = setOf("tool")
                )
            }

            UltraFrontierLane.BLOCKED -> {
                tasks += UltraFrontierTask(
                    id = "safety",
                    specialist = UltraFrontierSpecialist.SAFETY_GATE,
                    dependsOn = setOf("route")
                )
                tasks += UltraFrontierTask(
                    id = "critic",
                    specialist = UltraFrontierSpecialist.CRITIC,
                    dependsOn = setOf("safety")
                )
            }
        }

        return tasks
    }

    private fun shouldUseContext(message: String): Boolean {
        val normalized = message.lowercase(Locale.ROOT)
        return normalized.length >= 24 ||
            normalized.contains("mi ") ||
            normalized.contains("antes") ||
            normalized.contains("recuerda") ||
            normalized.contains("otra vez")
    }
}

class UltraFrontierSpecialistExecutor {
    fun <T> execute(
        tasks: List<UltraFrontierTask>,
        maxParallelism: Int,
        timeoutMillis: Long,
        handler: (UltraFrontierTask) -> T
    ): Map<String, T> {
        require(maxParallelism >= 1)
        require(timeoutMillis >= 1L)
        val pending = tasks.associateBy { it.id }.toMutableMap()
        require(pending.size == tasks.size) {
            "Frontier tasks must have unique ids."
        }
        val results = linkedMapOf<String, T>()
        val completed = linkedSetOf<String>()
        val executor = Executors.newFixedThreadPool(maxParallelism)
        val startedNanos = System.nanoTime()

        try {
            while (pending.isNotEmpty()) {
                val ready = pending.values.filter {
                    it.dependsOn.all(completed::contains)
                }
                require(ready.isNotEmpty()) {
                    "Frontier task graph contains a cycle or missing dependency."
                }

                val futures = ready.associateWith { task ->
                    executor.submit<T> { handler(task) }
                }
                for ((task, future) in futures) {
                    val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(
                        System.nanoTime() - startedNanos
                    )
                    val remaining = timeoutMillis - elapsedMillis
                    if (remaining <= 0L) {
                        throw TimeoutException(
                            "Frontier specialist execution budget exhausted."
                        )
                    }
                    results[task.id] = future.get(
                        remaining,
                        TimeUnit.MILLISECONDS
                    )
                    completed += task.id
                    pending.remove(task.id)
                }
            }
        } finally {
            executor.shutdownNow()
        }

        return results
    }
}

data class UltraFrontierComputeBudget(
    val sourceBudget: Int,
    val passBudget: Int,
    val timeBudgetMillis: Long,
    val maxParallelism: Int,
    val minimumIndependentSources: Int
)

class UltraAdaptiveComputeBudgeter {
    fun budget(
        request: UltraGeneralQueryRequest,
        policy: UltraFrontierPolicy,
        failurePressure: Int,
        worldState: UltraFrontierWorldState?
    ): UltraFrontierComputeBudget {
        val hard = request.kind == UltraGeneralQueryKind.COMPARISON_RESEARCH
        val fresh = request.requiresFreshData ||
            request.kind == UltraGeneralQueryKind.CURRENT_DATA
        val baseSource = when {
            hard -> policy.deepSourceBudget
            request.verificationMode == UltraVerificationMode.REQUIRED ->
                policy.verifiedSourceBudget
            else -> policy.fastSourceBudget
        }
        val maxSource = policy.deepSourceBudget
        val sourceWithPressure =
            (baseSource + failurePressure.coerceIn(0, 3))
                .coerceAtMost(maxSource)
        val passBudget = when {
            hard -> policy.deepResearchPassBudget
            request.verificationMode == UltraVerificationMode.REQUIRED ->
                policy.verifiedResearchPassBudget
            else -> 1
        }
        val timeBudget = when {
            hard -> policy.deepResearchTimeBudgetMillis
            request.verificationMode == UltraVerificationMode.REQUIRED ->
                policy.verifiedResearchTimeBudgetMillis
            else -> minOf(
                request.timeoutMillis,
                policy.verifiedResearchTimeBudgetMillis
            )
        }
        val quorum = when {
            hard -> policy.minimumDeepSources
            fresh -> maxOf(
                policy.minimumVerifiedSources,
                policy.minimumFreshSources
            )
            else -> policy.minimumVerifiedSources
        }

        val constrained =
            worldState?.let {
                it.preventAggressiveProfiles ||
                    it.thermalRisk == com.cardenaspiero255.gamehubultra.domain.ThermalRisk.CRITICAL ||
                    (
                        it.batteryPercent != null &&
                            !it.charging &&
                            it.batteryPercent <= 15
                        )
            } == true

        return if (constrained) {
            UltraFrontierComputeBudget(
                sourceBudget = sourceWithPressure
                    .coerceAtMost(policy.verifiedSourceBudget)
                    .coerceAtLeast(quorum),
                passBudget = passBudget.coerceAtMost(
                    policy.verifiedResearchPassBudget
                ),
                timeBudgetMillis = timeBudget.coerceAtMost(
                    policy.verifiedResearchTimeBudgetMillis
                ),
                maxParallelism = 1,
                minimumIndependentSources = quorum
            )
        } else {
            UltraFrontierComputeBudget(
                sourceBudget = sourceWithPressure,
                passBudget = passBudget,
                timeBudgetMillis = timeBudget,
                maxParallelism = if (hard) 4 else 2,
                minimumIndependentSources = quorum
            )
        }
    }
}

data class UltraFrontierEvaluationSnapshot(
    val total: Int,
    val accepted: Int,
    val verified: Int,
    val abstained: Int,
    val averageLatencyMillis: Long,
    val failureReasons: Map<String, Int>
)

class UltraFrontierEvaluationRegistry(
    private val capacity: Int = 512
) {
    private val outcomes = ArrayDeque<UltraFrontierExecutionOutcome>()

    init {
        require(capacity >= 16)
    }

    @Synchronized
    fun record(outcome: UltraFrontierExecutionOutcome) {
        while (outcomes.size >= capacity) {
            outcomes.removeFirst()
        }
        outcomes.addLast(outcome)
    }

    @Synchronized
    fun snapshot(
        domain: UltraFrontierDomain
    ): UltraFrontierEvaluationSnapshot {
        val samples = outcomes.filter { it.domain == domain }
        val reasons = samples
            .mapNotNull { it.reasonCode }
            .groupingBy { it }
            .eachCount()
        return UltraFrontierEvaluationSnapshot(
            total = samples.size,
            accepted = samples.count { it.accepted },
            verified = samples.count { it.verified },
            abstained = samples.count { it.abstained },
            averageLatencyMillis = if (samples.isEmpty()) {
                0L
            } else {
                samples.sumOf { it.latencyMillis.coerceAtLeast(0L) } /
                    samples.size
            },
            failureReasons = reasons
        )
    }
}

data class UltraFrontierEnsembleCandidate(
    val message: String,
    val verified: Boolean,
    val confidence: UltraAnswerConfidence?,
    val independentSourceCount: Int,
    val latencyMillis: Long,
    val fresh: Boolean,
    val abstained: Boolean
)

class UltraFrontierEnsembleSelector {
    fun select(
        candidates: List<UltraFrontierEnsembleCandidate>
    ): UltraFrontierEnsembleCandidate? =
        candidates
            .filter { it.message.isNotBlank() }
            .maxByOrNull(::score)

    private fun score(candidate: UltraFrontierEnsembleCandidate): Long {
        val confidence = when (candidate.confidence) {
            UltraAnswerConfidence.HIGH -> 3L
            UltraAnswerConfidence.MEDIUM -> 2L
            UltraAnswerConfidence.LOW -> 1L
            null -> 0L
        }
        return (if (candidate.verified) 10_000L else 0L) +
            confidence * 1_000L +
            candidate.independentSourceCount.coerceAtLeast(0) * 300L +
            (if (candidate.fresh) 500L else 0L) -
            (if (candidate.abstained) 20_000L else 0L) -
            (candidate.latencyMillis.coerceAtLeast(0L) / 10L)
    }
}

class UltraFrontierEvolutionController(
    val learning: UltraFrontierLearningStore = UltraFrontierLearningStore(),
    val providerRanker: UltraAdaptiveProviderRanker =
        UltraAdaptiveProviderRanker(),
    val consensus: UltraWeightedConsensusEngine =
        UltraWeightedConsensusEngine(),
    val claimVerifier: UltraClaimVerifier = UltraClaimVerifier(),
    val knowledgeGraph: UltraTemporalKnowledgeGraph =
        UltraTemporalKnowledgeGraph(),
    val planner: UltraFrontierTaskPlanner = UltraFrontierTaskPlanner(),
    val budgeter: UltraAdaptiveComputeBudgeter =
        UltraAdaptiveComputeBudgeter(),
    val evaluation: UltraFrontierEvaluationRegistry =
        UltraFrontierEvaluationRegistry(),
    val ensemble: UltraFrontierEnsembleSelector =
        UltraFrontierEnsembleSelector()
) {
    fun domain(request: UltraGeneralQueryRequest): UltraFrontierDomain =
        UltraFrontierDomainClassifier.classify(request)

    fun budget(
        request: UltraGeneralQueryRequest,
        policy: UltraFrontierPolicy
    ): UltraFrontierComputeBudget {
        val domain = domain(request)
        return budgeter.budget(
            request = request,
            policy = policy,
            failurePressure = learning.failurePressure(domain),
            worldState = UltraFrontierWorldStateRegistry.snapshot()
        )
    }

    fun tasks(
        request: UltraFrontierRequest,
        lane: UltraFrontierLane
    ): List<UltraFrontierTask> =
        planner.plan(request, lane)

    fun record(outcome: UltraFrontierExecutionOutcome) {
        learning.record(outcome)
        evaluation.record(outcome)
    }

    fun finalGate(
        request: UltraGeneralQueryRequest,
        plan: UltraFrontierPlan,
        answer: UltraQueryExecutionAnswer,
        nowMillis: Long
    ): UltraQueryExecutionAnswer {
        if (answer.abstained || !answer.verified) return answer
        if (
            plan.lane != UltraFrontierLane.VERIFIED_RESEARCH &&
            plan.lane != UltraFrontierLane.DEEP_RESEARCH
        ) {
            return answer
        }

        val status = claimVerifier.verify(
            claim = UltraFrontierClaim(
                id = request.correlationId,
                text = answer.message,
                confidence = answer.confidence ?: UltraAnswerConfidence.LOW,
                independentSourceCount = answer.independentSourceCount,
                validUntilMillis = if (request.requiresFreshData) {
                    safeAdd(nowMillis, UltraResearchCache.CURRENT_DATA_TTL_MS)
                } else {
                    null
                }
            ),
            minimumIndependentSources =
                plan.minimumDistinctSources.coerceAtLeast(1),
            nowMillis = nowMillis
        )
        if (status == UltraClaimStatus.VERIFIED) return answer

        return answer.copy(
            message =
                "Encontré una respuesta candidata, pero una de sus afirmaciones no " +
                    "alcanzó el nivel de corroboración exigido. Prefiero no presentarla como un hecho.",
            verified = false,
            sources = emptyList(),
            abstained = true,
            retryable = false,
            reasonCode = "FRONTIER_CLAIM_QUORUM",
            stage = "frontier-claim-verifier"
        )
    }

    fun recallVerified(
        request: UltraGeneralQueryRequest,
        nowMillis: Long
    ): UltraQueryExecutionAnswer? {
        if (request.requiresFreshData) return null
        val subject = request.originalText
            .trim()
            .lowercase(Locale.ROOT)
        val fact = knowledgeGraph.resolve(
            subject = subject,
            predicate = "answer",
            atMillis = nowMillis
        ) ?: return null
        if (fact.confidence == UltraAnswerConfidence.LOW) return null

        return UltraQueryExecutionAnswer(
            message = fact.value,
            verified = true,
            confidence = fact.confidence,
            sources = fact.sourceIds.toList(),
            independentSourceCount = fact.sourceIds.size,
            abstained = false,
            retryable = false,
            stage = "frontier-knowledge-graph"
        )
    }

    fun rememberVerified(
        request: UltraGeneralQueryRequest,
        result: UltraQueryExecutionAnswer,
        nowMillis: Long
    ) {
        if (
            result.abstained ||
            !result.verified ||
            result.message.isBlank()
        ) {
            return
        }
        val ttl = when (request.kind) {
            UltraGeneralQueryKind.CURRENT_DATA ->
                UltraResearchCache.CURRENT_DATA_TTL_MS
            UltraGeneralQueryKind.COMPARISON_RESEARCH ->
                UltraResearchCache.COMPARISON_TTL_MS
            UltraGeneralQueryKind.GENERAL_KNOWLEDGE ->
                UltraResearchCache.GENERAL_KNOWLEDGE_TTL_MS
        }
        knowledgeGraph.upsert(
            UltraTemporalFact(
                subject = request.originalText
                    .trim()
                    .lowercase(Locale.ROOT),
                predicate = "answer",
                value = result.message,
                validFromMillis = nowMillis,
                validUntilMillis = safeAdd(nowMillis, ttl),
                confidence = result.confidence ?: UltraAnswerConfidence.MEDIUM,
                sourceIds = result.sources.toSet()
            )
        )
    }

    private fun safeAdd(left: Long, right: Long): Long {
        val remaining = Long.MAX_VALUE - left
        return if (right > remaining) Long.MAX_VALUE else left + right
    }
}
