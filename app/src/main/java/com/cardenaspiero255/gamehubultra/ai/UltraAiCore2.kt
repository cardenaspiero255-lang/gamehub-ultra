package com.cardenaspiero255.gamehubultra.ai

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
            latencyMs = context.networkLatencyMs?.takeIf { it in 0..Int.MAX_VALUE.toLong() }?.toInt(),
            sessionActive = context.sessionActive
        ).sanitized()
    }
}

data class UltraAiFeedbackSnapshot(
    val acceptedProfileIds: Set<String> = emptySet(),
    val rejectedProfileIds: Set<String> = emptySet()
)

data class UltraAiMemorySignal(val text: String, val provenance: UltraMemoryProvenance) {
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
    fun recommend(observation: UltraAiObservation, feedback: UltraAiFeedbackSnapshot): UltraAiRecommendation
}

data class UltraAiCoreResult(
    val recommendation: UltraAiRecommendation,
    val explanation: String,
    val memorySignals: List<UltraAiMemorySignal>,
    val requiresCloud: Boolean
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
        val recommendation = recommender?.recommend(safeObservation, feedback)
            ?: deterministicRecommendation(safeObservation, feedback)
        return UltraAiCoreResult(
            recommendation = recommendation,
            explanation = buildExplanation(safeObservation, recommendation),
            memorySignals = memories,
            requiresCloud = false
        )
    }

    private fun deterministicRecommendation(
        observation: UltraAiObservation,
        feedback: UltraAiFeedbackSnapshot
    ): UltraAiRecommendation {
        val evidence = mutableListOf<String>()
        val battery = observation.batteryPercent
        val thermal = observation.thermalLabel?.trim()?.lowercase()
        val profile = when {
            battery != null && battery <= LOW_BATTERY_PERCENT -> {
                evidence += "battery=$battery"
                "BALANCED"
            }
            thermal in HOT_THERMAL_LABELS -> {
                evidence += "thermal=" + observation.thermalLabel
                "BALANCED"
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
        when {
            profile in feedback.rejectedProfileIds -> evidence += "feedback=rejected"
            profile in feedback.acceptedProfileIds -> evidence += "feedback=accepted"
        }
        return UltraAiRecommendation(
            profileId = profile,
            confidence = LOCAL_CONFIDENCE,
            evidence = evidence,
            source = UltraAiRecommendationSource.DETERMINISTIC_LOCAL
        )
    }

    private fun buildExplanation(
        observation: UltraAiObservation,
        recommendation: UltraAiRecommendation
    ): String {
        val evidence = recommendation.evidence.filter { it.isNotBlank() }
            .joinToString(", ").ifBlank { "sin métricas adicionales disponibles" }
        val metrics = buildList {
            observation.batteryPercent?.let { add("batería=$it%") }
            observation.thermalLabel?.takeIf { it.isNotBlank() }?.let { add("estado térmico=$it") }
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
        }
    }

    private companion object {
        const val LOW_BATTERY_PERCENT = 20
        const val LOCAL_CONFIDENCE = 0.72
        val HOT_THERMAL_LABELS = setOf("alto", "alta", "hot", "severo", "severe", "critical", "crítico")
    }
}
