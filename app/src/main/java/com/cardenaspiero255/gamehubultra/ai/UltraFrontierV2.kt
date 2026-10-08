package com.cardenaspiero255.gamehubultra.ai

import java.util.Locale
import kotlin.math.floor

/**
 * CAR-51 / Frontier V2.
 *
 * Adds explicit hierarchy, dynamic replanning, adaptive branch allocation and
 * claim provenance without replacing the verified CAR-50 core.
 */
enum class UltraFrontierV2Phase {
    ROUTING,
    CONTEXT,
    EVIDENCE,
    SYNTHESIS,
    VERIFICATION,
    ACTION,
    CRITIQUE
}

data class UltraFrontierV2Objective(
    val id: String,
    val requiresVerification: Boolean,
    val requiresFreshEvidence: Boolean
)

data class UltraFrontierV2PlanPhase(
    val phase: UltraFrontierV2Phase,
    val taskIds: Set<String>
)

data class UltraFrontierV2HierarchicalPlan(
    val objective: UltraFrontierV2Objective,
    val phases: List<UltraFrontierV2PlanPhase>
) {
    init {
        require(phases.isNotEmpty())
        require(phases.first().phase == UltraFrontierV2Phase.ROUTING)
    }
}

class UltraFrontierV2HierarchicalPlanner {
    fun plan(
        request: UltraFrontierRequest,
        lane: UltraFrontierLane,
        tasks: List<UltraFrontierTask>
    ): UltraFrontierV2HierarchicalPlan {
        val grouped = linkedMapOf<UltraFrontierV2Phase, LinkedHashSet<String>>()
        tasks.forEach { task ->
            grouped
                .getOrPut(task.specialist.toV2Phase()) { linkedSetOf() }
                .add(task.id)
        }

        val orderedPhases = PHASE_ORDER.mapNotNull { phase ->
            grouped[phase]
                ?.takeIf { it.isNotEmpty() }
                ?.let { UltraFrontierV2PlanPhase(phase, it) }
        }

        val objective = UltraFrontierV2Objective(
            id = when (lane) {
                UltraFrontierLane.DEEP_RESEARCH -> "deep-research-verified-answer"
                UltraFrontierLane.VERIFIED_RESEARCH -> "verified-answer"
                UltraFrontierLane.TOOL_ACTION -> "safe-tool-action"
                UltraFrontierLane.BLOCKED -> "safe-abstention"
                UltraFrontierLane.LOCAL_DELIBERATE -> "deliberated-local-answer"
                UltraFrontierLane.LOCAL_FAST -> "fast-local-answer"
            },
            requiresVerification =
                lane == UltraFrontierLane.DEEP_RESEARCH ||
                    lane == UltraFrontierLane.VERIFIED_RESEARCH ||
                    request.query.verificationMode == UltraVerificationMode.REQUIRED,
            requiresFreshEvidence = request.query.requiresFreshData
        )

        return UltraFrontierV2HierarchicalPlan(
            objective = objective,
            phases = orderedPhases.ifEmpty {
                listOf(
                    UltraFrontierV2PlanPhase(
                        phase = UltraFrontierV2Phase.ROUTING,
                        taskIds = setOf("route")
                    )
                )
            }
        )
    }

    private fun UltraFrontierSpecialist.toV2Phase(): UltraFrontierV2Phase =
        when (this) {
            UltraFrontierSpecialist.ROUTER ->
                UltraFrontierV2Phase.ROUTING
            UltraFrontierSpecialist.MEMORY,
            UltraFrontierSpecialist.TELEMETRY,
            UltraFrontierSpecialist.MULTIMODAL ->
                UltraFrontierV2Phase.CONTEXT
            UltraFrontierSpecialist.RESEARCH,
            UltraFrontierSpecialist.LOCAL_REASONER ->
                UltraFrontierV2Phase.EVIDENCE
            UltraFrontierSpecialist.SYNTHESIZER ->
                UltraFrontierV2Phase.SYNTHESIS
            UltraFrontierSpecialist.VERIFIER ->
                UltraFrontierV2Phase.VERIFICATION
            UltraFrontierSpecialist.TOOL_GATEWAY,
            UltraFrontierSpecialist.SAFETY_GATE ->
                UltraFrontierV2Phase.ACTION
            UltraFrontierSpecialist.CRITIC ->
                UltraFrontierV2Phase.CRITIQUE
        }

    private companion object {
        val PHASE_ORDER = listOf(
            UltraFrontierV2Phase.ROUTING,
            UltraFrontierV2Phase.CONTEXT,
            UltraFrontierV2Phase.EVIDENCE,
            UltraFrontierV2Phase.SYNTHESIS,
            UltraFrontierV2Phase.VERIFICATION,
            UltraFrontierV2Phase.ACTION,
            UltraFrontierV2Phase.CRITIQUE
        )
    }
}

data class UltraFrontierV2ReplanResult(
    val tasks: List<UltraFrontierTask>,
    val changed: Boolean,
    val replacedTaskIds: Set<String>
)

class UltraFrontierV2Replanner(
    private val minimumRecoveryTimeMillis: Long = 2_000L
) {
    init {
        require(minimumRecoveryTimeMillis >= 1L)
    }

    fun replan(
        tasks: List<UltraFrontierTask>,
        failedTaskIds: Set<String>,
        remainingTimeMillis: Long,
        recoveryOrdinal: Int
    ): UltraFrontierV2ReplanResult {
        require(recoveryOrdinal >= 1)
        val failedResearch = tasks.filter {
            it.id in failedTaskIds &&
                it.specialist == UltraFrontierSpecialist.RESEARCH
        }
        if (
            failedResearch.isEmpty() ||
            remainingTimeMillis < minimumRecoveryTimeMillis
        ) {
            return UltraFrontierV2ReplanResult(
                tasks = tasks,
                changed = false,
                replacedTaskIds = emptySet()
            )
        }

        val replacementByFailed = linkedMapOf<String, UltraFrontierTask>()
        failedResearch.forEachIndexed { index, failed ->
            val replacementId = "research-recovery-" + (recoveryOrdinal + index)
            replacementByFailed[failed.id] = failed.copy(id = replacementId)
        }

        val rebuilt = tasks
            .filterNot { it.id in replacementByFailed }
            .map { task ->
                val dependencies = task.dependsOn.flatMapTo(linkedSetOf()) { dependency ->
                    listOf(replacementByFailed[dependency]?.id ?: dependency)
                }
                task.copy(dependsOn = dependencies)
            }
            .toMutableList()

        failedResearch.forEach { failed ->
            replacementByFailed[failed.id]?.let(rebuilt::add)
        }

        return UltraFrontierV2ReplanResult(
            tasks = rebuilt,
            changed = true,
            replacedTaskIds = replacementByFailed.keys
        )
    }
}

data class UltraFrontierV2BranchSignal(
    val taskId: String,
    val reliability: Double,
    val novelty: Double,
    val urgency: Double
) {
    init {
        require(taskId.isNotBlank())
        require(reliability in 0.0..1.0)
        require(novelty in 0.0..1.0)
        require(urgency in 0.0..1.0)
    }

    val score: Double
        get() =
            reliability * 0.50 +
                novelty * 0.30 +
                urgency * 0.20
}

class UltraFrontierV2BranchAllocator {
    fun allocate(
        totalSourceBudget: Int,
        maxParallelism: Int,
        signals: List<UltraFrontierV2BranchSignal>
    ): Map<String, Int> {
        require(totalSourceBudget >= 0)
        require(maxParallelism >= 1)
        if (signals.isEmpty() || totalSourceBudget == 0) return emptyMap()

        val active = signals
            .distinctBy { it.taskId }
            .sortedWith(
                compareByDescending<UltraFrontierV2BranchSignal> { it.score }
                    .thenBy { it.taskId }
            )
            .take(minOf(maxParallelism, totalSourceBudget, signals.size))

        if (active.isEmpty()) return emptyMap()

        val allocation = active.associate { it.taskId to 1 }.toMutableMap()
        val distributable = totalSourceBudget - active.size
        if (distributable <= 0) return allocation

        val totalScore = active.sumOf { it.score.coerceAtLeast(MINIMUM_WEIGHT) }
        val fractional = mutableListOf<Pair<String, Double>>()
        var assignedExtra = 0

        active.forEach { signal ->
            val exact =
                distributable * signal.score.coerceAtLeast(MINIMUM_WEIGHT) / totalScore
            val whole = floor(exact).toInt()
            allocation[signal.taskId] = allocation.getValue(signal.taskId) + whole
            assignedExtra += whole
            fractional += signal.taskId to (exact - whole)
        }

        var leftovers = distributable - assignedExtra
        fractional
            .sortedWith(
                compareByDescending<Pair<String, Double>> { it.second }
                    .thenBy { it.first }
            )
            .forEach { (taskId, _) ->
                if (leftovers <= 0) return@forEach
                allocation[taskId] = allocation.getValue(taskId) + 1
                leftovers -= 1
            }

        return allocation
    }

    private companion object {
        const val MINIMUM_WEIGHT = 0.05
    }
}

data class UltraFrontierV2ClaimEvidence(
    val claimId: String,
    val normalizedValue: String,
    val sourceId: String,
    val providerId: String,
    val authoritative: Boolean
) {
    init {
        require(claimId.isNotBlank())
        require(normalizedValue.isNotBlank())
        require(sourceId.isNotBlank())
        require(providerId.isNotBlank())
    }
}

data class UltraFrontierV2ClaimProvenanceSnapshot(
    val claimId: String,
    val preferredValue: String,
    val supportingSources: Int,
    val conflictingSources: Int,
    val independentSources: Int,
    val providers: Int,
    val hasConflict: Boolean,
    val hasAuthoritativeSupport: Boolean
)

class UltraFrontierV2ClaimProvenanceGraph {
    private val evidenceByClaim =
        linkedMapOf<String, LinkedHashMap<String, UltraFrontierV2ClaimEvidence>>()

    @Synchronized
    fun record(evidence: UltraFrontierV2ClaimEvidence) {
        val normalized = evidence.copy(
            claimId = normalize(evidence.claimId),
            normalizedValue = normalize(evidence.normalizedValue),
            sourceId = normalize(evidence.sourceId),
            providerId = normalize(evidence.providerId)
        )
        val claimEvidence = evidenceByClaim.getOrPut(normalized.claimId) {
            linkedMapOf()
        }
        val identity =
            normalized.sourceId + "|" +
                normalized.providerId + "|" +
                normalized.normalizedValue
        claimEvidence[identity] = normalized
    }

    @Synchronized
    fun snapshot(
        claimId: String,
        preferredValue: String
    ): UltraFrontierV2ClaimProvenanceSnapshot? {
        val normalizedClaim = normalize(claimId)
        val normalizedPreferred = normalize(preferredValue)
        val evidence = evidenceByClaim[normalizedClaim]
            ?.values
            ?.toList()
            .orEmpty()
        if (evidence.isEmpty()) return null

        val supporting = evidence.filter {
            it.normalizedValue == normalizedPreferred
        }
        val conflicting = evidence.filter {
            it.normalizedValue != normalizedPreferred
        }

        return UltraFrontierV2ClaimProvenanceSnapshot(
            claimId = normalizedClaim,
            preferredValue = normalizedPreferred,
            supportingSources = supporting.map { it.sourceId }.distinct().size,
            conflictingSources = conflicting.map { it.sourceId }.distinct().size,
            independentSources = evidence.map { it.sourceId }.distinct().size,
            providers = evidence.map { it.providerId }.distinct().size,
            hasConflict = conflicting.isNotEmpty(),
            hasAuthoritativeSupport = supporting.any { it.authoritative }
        )
    }

    @Synchronized
    fun clear() {
        evidenceByClaim.clear()
    }

    private fun normalize(value: String): String =
        value.trim().lowercase(Locale.ROOT)
}
