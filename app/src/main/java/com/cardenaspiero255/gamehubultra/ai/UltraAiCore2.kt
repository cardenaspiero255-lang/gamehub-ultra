package com.cardenaspiero255.gamehubultra.ai

import com.cardenaspiero255.gamehubultra.domain.OptimizationFeedbackDecision
import com.cardenaspiero255.gamehubultra.domain.OptimizationObservation

enum class UltraAiRecommendationSource { MODEL, DETERMINISTIC_LOCAL }

data class UltraAiObservation(
    val gamePackage: String,
    val activeProfileId: String,
    val batteryPercent: Int? = null,
    val thermalLabel: String? = null,
    val refreshRateHz: Float? = null,
    val latencyMs: Int? = null,
    val thermalStatus: Int? = null,
    val thermalHeadroom: Float? = null,
    val sessionActive: Boolean = false
) {
    fun sanitized(): UltraAiObservation = copy(
        batteryPercent = batteryPercent?.takeIf { it in 0..100 },
        refreshRateHz = refreshRateHz?.takeIf { it.isFinite() && it > 0f },
        latencyMs = latencyMs?.takeIf { it >= 0 },
        thermalStatus = thermalStatus?.takeIf { it >= 0 },
        thermalHeadroom = thermalHeadroom?.takeIf { it.isFinite() && it >= 0f }
    )

    companion object {
        fun from(context: GameHubAiContext): UltraAiObservation = UltraAiObservation(
            gamePackage = context.selectedGamePackage.orEmpty(),
            activeProfileId = context.selectedProfile.name,
            batteryPercent = context.batteryPercent,
            thermalStatus = context.thermalStatus,
            thermalHeadroom = context.thermalHeadroom,
            refreshRateHz = context.refreshRateHz,
            latencyMs = context.networkLatencyMs
                ?.takeIf { it in 0..Int.MAX_VALUE.toLong() }
                ?.toInt(),
            sessionActive = context.sessionActive
        ).sanitized()
    }
}

data class UltraAiFeedbackSnapshot(
    val acceptedProfileIds: Set<String> = emptySet(),
    val rejectedProfileIds: Set<String> = emptySet(),
    val revertedProfileIds: Set<String> = emptySet(),
    val rejectedProfileCounts: Map<String, Int> = emptyMap(),
    val revertedProfileCounts: Map<String, Int> = emptyMap()
) {
    fun rejectedCount(profileId: String): Int = maxOf(
        rejectedProfileCounts.profileCount(profileId),
        if (rejectedProfileIds.containsProfile(profileId)) 1 else 0
    )

    fun revertedCount(profileId: String): Int = maxOf(
        revertedProfileCounts.profileCount(profileId),
        if (revertedProfileIds.containsProfile(profileId)) 1 else 0
    )

    fun poorOutcomeCount(profileId: String): Int =
        rejectedCount(profileId) + revertedCount(profileId)

    fun isAccepted(profileId: String): Boolean =
        acceptedProfileIds.containsProfile(profileId)

    fun hasContradiction(profileId: String): Boolean =
        isAccepted(profileId) && poorOutcomeCount(profileId) > 0

    companion object {
        fun fromObservations(
            observations: List<OptimizationObservation>
        ): UltraAiFeedbackSnapshot {
            val accepted = observations.filter {
                it.feedbackDecision == OptimizationFeedbackDecision.ACCEPTED
            }
            val rejected = observations.filter {
                it.feedbackDecision == OptimizationFeedbackDecision.REJECTED
            }
            val reverted = observations.filter {
                it.feedbackDecision == OptimizationFeedbackDecision.REVERTED
            }
            return UltraAiFeedbackSnapshot(
                acceptedProfileIds = accepted.mapTo(linkedSetOf()) { it.profile.name },
                rejectedProfileIds = rejected.mapTo(linkedSetOf()) { it.profile.name },
                revertedProfileIds = reverted.mapTo(linkedSetOf()) { it.profile.name },
                rejectedProfileCounts = rejected
                    .groupingBy { it.profile.name }
                    .eachCount(),
                revertedProfileCounts = reverted
                    .groupingBy { it.profile.name }
                    .eachCount()
            )
        }
    }
}

private fun Set<String>.containsProfile(profileId: String): Boolean =
    any { it.equals(profileId, ignoreCase = true) }

private fun Map<String, Int>.profileCount(profileId: String): Int =
    entries
        .asSequence()
        .filter { it.key.equals(profileId, ignoreCase = true) }
        .sumOf { it.value.coerceAtLeast(0) }

data class UltraAiMemorySignal(
    val text: String,
    val provenance: UltraMemoryProvenance
) {
    companion object {
        fun from(recall: UltraMemoryRecall): UltraAiMemorySignal = UltraAiMemorySignal(
            text = recall.record.text,
            provenance = recall.provenance
        )
    }
}

data class UltraAiRecommendation(
    val profileId: String,
    val confidence: Double,
    val evidence: List<String>,
    val source: UltraAiRecommendationSource = UltraAiRecommendationSource.MODEL
)

fun interface UltraAiRecommender {
    fun recommend(
        observation: UltraAiObservation,
        feedback: UltraAiFeedbackSnapshot,
        memories: List<UltraAiMemorySignal>
    ): UltraAiRecommendation
}

data class UltraAiCoreResult(
    val recommendation: UltraAiRecommendation,
    val explanation: String,
    val memorySignals: List<UltraAiMemorySignal>,
    val requiresCloud: Boolean,
    val recoveryExplanation: String? = null
)

interface UltraAiCoreGateway {
    fun evaluate(
        observation: UltraAiObservation,
        feedback: UltraAiFeedbackSnapshot,
        memories: List<UltraAiMemorySignal>
    ): UltraAiCoreResult
}

class UltraAiCore2(
    private val recommender: UltraAiRecommender? = null
) : UltraAiCoreGateway {
    override fun evaluate(
        observation: UltraAiObservation,
        feedback: UltraAiFeedbackSnapshot,
        memories: List<UltraAiMemorySignal>
    ): UltraAiCoreResult {
        val safeObservation = observation.sanitized()
        val rawRecommendation = recommender?.recommend(
            safeObservation,
            feedback,
            memories
        ) ?: deterministicRecommendation(safeObservation, memories)
        val recommendation = recoverRecommendation(
            observation = safeObservation,
            recommendation = rawRecommendation,
            feedback = feedback
        )
        return UltraAiCoreResult(
            recommendation = recommendation,
            explanation = buildExplanation(safeObservation, recommendation),
            memorySignals = memories,
            requiresCloud = false,
            recoveryExplanation = buildRecoveryExplanation(recommendation)
        )
    }

    private fun deterministicRecommendation(
        observation: UltraAiObservation,
        memories: List<UltraAiMemorySignal>
    ): UltraAiRecommendation {
        val evidence = mutableListOf<String>()
        val battery = observation.batteryPercent
        val thermal = observation.thermalLabel?.trim()?.lowercase()
        val hotThermal = thermal in HOT_THERMAL_LABELS ||
            observation.thermalStatus?.let { it >= 3 } == true ||
            observation.thermalHeadroom?.let { it >= 0.80f } == true
        val safeMemoryPreference = rememberedSafeProfilePreference(memories)
        val profile = when {
            battery != null && battery < LOW_BATTERY_PERCENT -> {
                evidence += "battery=$battery"
                "BALANCED"
            }
            hotThermal -> {
                val thermalEvidence = if (thermal in HOT_THERMAL_LABELS) {
                    "thermal=" + observation.thermalLabel
                } else if ((observation.thermalStatus ?: -1) >= 3) {
                    "thermalStatus=" + observation.thermalStatus
                } else {
                    "thermalHeadroom=" + observation.thermalHeadroom
                }
                evidence += thermalEvidence
                "BALANCED"
            }
            safeMemoryPreference != null -> {
                evidence += "memoryPreference=$safeMemoryPreference"
                safeMemoryPreference
            }
            observation.activeProfileId.isNotBlank() -> {
                evidence += "activeProfile=" + observation.activeProfileId
                observation.activeProfileId
            }
            else -> {
                evidence += "safe-default"
                "BALANCED"
            }
        }
        return UltraAiRecommendation(
            profileId = profile,
            confidence = LOCAL_CONFIDENCE,
            evidence = evidence,
            source = UltraAiRecommendationSource.DETERMINISTIC_LOCAL
        )
    }

    private fun recoverRecommendation(
        observation: UltraAiObservation,
        recommendation: UltraAiRecommendation,
        feedback: UltraAiFeedbackSnapshot
    ): UltraAiRecommendation {
        val originalProfile = recommendation.profileId
            .trim()
            .ifBlank { observation.activeProfileId.trim().ifBlank { "BALANCED" } }
        val rejectedCount = feedback.rejectedCount(originalProfile)
        val revertedCount = feedback.revertedCount(originalProfile)
        val poorOutcomeCount = rejectedCount + revertedCount
        val contradiction = feedback.hasContradiction(originalProfile)
        val evidence = recommendation.evidence
            .filter(String::isNotBlank)
            .toMutableList()

        when {
            contradiction -> evidence += "feedback=contradiction"
            revertedCount > 0 -> evidence += "feedback=reverted"
            rejectedCount > 0 -> evidence += "feedback=rejected"
            feedback.isAccepted(originalProfile) -> evidence += "feedback=accepted"
        }

        val confidencePenalty = (
            rejectedCount * REJECTION_CONFIDENCE_PENALTY +
                revertedCount * REVERSION_CONFIDENCE_PENALTY +
                if (contradiction) CONTRADICTION_CONFIDENCE_PENALTY else 0.0
            ).coerceAtMost(MAX_CONFIDENCE_PENALTY)
        if (confidencePenalty > 0.0) {
            evidence += "recovery=confidence-reduced"
        }

        val recoveredProfile = if (poorOutcomeCount >= REPEATED_POOR_OUTCOME_THRESHOLD) {
            selectRecoveryProfile(
                originalProfile = originalProfile,
                observation = observation,
                feedback = feedback
            )
        } else {
            originalProfile
        }
        if (!recoveredProfile.equals(originalProfile, ignoreCase = true)) {
            evidence += "recovery=poor-history"
            evidence += "recoveryFrom=$originalProfile"
        }

        val baseConfidence = recommendation.confidence
            .takeIf(Double::isFinite)
            ?.coerceIn(0.0, 1.0)
            ?: 0.0
        val recoveryFloor = minOf(MIN_RECOVERY_CONFIDENCE, baseConfidence)
        return recommendation.copy(
            profileId = recoveredProfile,
            confidence = (baseConfidence - confidencePenalty)
                .coerceIn(recoveryFloor, 1.0),
            evidence = evidence.distinct()
        )
    }

    private fun selectRecoveryProfile(
        originalProfile: String,
        observation: UltraAiObservation,
        feedback: UltraAiFeedbackSnapshot
    ): String {
        val acceptedAlternative = feedback.acceptedProfileIds
            .asSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .filterNot { it.equals(originalProfile, ignoreCase = true) }
            .filter { feedback.poorOutcomeCount(it) == 0 }
            .sortedWith(
                compareBy<String> { if (it.equals("BALANCED", ignoreCase = true)) 0 else 1 }
                    .thenBy(String::uppercase)
            )
            .firstOrNull()
        if (acceptedAlternative != null) return acceptedAlternative

        val activeProfile = observation.activeProfileId.trim()
        if (
            activeProfile.isNotBlank() &&
            !activeProfile.equals(originalProfile, ignoreCase = true) &&
            feedback.poorOutcomeCount(activeProfile) == 0
        ) {
            return activeProfile
        }

        return if (
            !originalProfile.equals("BALANCED", ignoreCase = true) &&
            feedback.poorOutcomeCount("BALANCED") < REPEATED_POOR_OUTCOME_THRESHOLD
        ) {
            "BALANCED"
        } else {
            originalProfile
        }
    }

    private fun rememberedSafeProfilePreference(
        memories: List<UltraAiMemorySignal>
    ): String? =
        memories
            .asSequence()
            .filter { it.provenance == UltraMemoryProvenance.REMEMBERED_FACT }
            .map { it.text.trim().lowercase() }
            .firstOrNull { text ->
                val expressesPreference =
                    "prefiero" in text ||
                    "priorizo" in text ||
                    "mi preferencia" in text ||
                    "perfil favorito" in text
                val prefersStability =
                    "estabilidad" in text ||
                    "estable" in text ||
                    "balanceado" in text ||
                    "equilibrado" in text
                expressesPreference && prefersStability
            }
            ?.let { "BALANCED" }

    private fun buildExplanation(
        observation: UltraAiObservation,
        recommendation: UltraAiRecommendation
    ): String {
        val evidence = recommendation.evidence
            .filter { it.isNotBlank() }
            .joinToString(", ")
            .ifBlank { "sin métricas adicionales disponibles" }
        val metrics = buildList {
            observation.batteryPercent?.let { add("batería=$it%") }
            observation.thermalLabel
                ?.takeIf { it.isNotBlank() }
                ?.let { add("estado térmico=$it") }
            observation.refreshRateHz?.let { add("refresco=" + it.toInt() + " Hz") }
            observation.latencyMs?.let { add("latencia=$it ms") }
        }
        return buildString {
            append("Recomiendo ")
            append(recommendation.profileId)
            append(" porque ")
            append(evidence)
            if (metrics.isNotEmpty()) {
                append(". Datos disponibles: ")
                append(metrics.joinToString(", "))
            }
            buildRecoveryExplanation(recommendation)?.let { recovery ->
                append(". ")
                append(recovery)
            }
        }
    }

    private fun buildRecoveryExplanation(
        recommendation: UltraAiRecommendation
    ): String? = when {
        "recovery=poor-history" in recommendation.evidence ->
            "Ajusté la recomendación porque el rendimiento previo de una sugerencia " +
                "fue desfavorable o se revirtió repetidamente."
        "feedback=contradiction" in recommendation.evidence ->
            "Ajusté la confianza porque detecté feedback contradictorio sobre esta recomendación."
        "recovery=confidence-reduced" in recommendation.evidence ->
            "Ajusté la confianza porque una recomendación previa fue rechazada o revertida."
        else -> null
    }

    private companion object {
        const val LOW_BATTERY_PERCENT = 20
        const val LOCAL_CONFIDENCE = 0.72
        const val REPEATED_POOR_OUTCOME_THRESHOLD = 2
        const val REJECTION_CONFIDENCE_PENALTY = 0.12
        const val REVERSION_CONFIDENCE_PENALTY = 0.18
        const val CONTRADICTION_CONFIDENCE_PENALTY = 0.08
        const val MAX_CONFIDENCE_PENALTY = 0.60
        const val MIN_RECOVERY_CONFIDENCE = 0.05
        val HOT_THERMAL_LABELS = setOf(
            "alto",
            "alta",
            "hot",
            "severo",
            "severe",
            "critical",
            "crítico"
        )
    }
}
