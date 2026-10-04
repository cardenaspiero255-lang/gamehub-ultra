package com.cardenaspiero255.gamehubultra.ai

enum class UltraAiRecommendationSource { MODEL, DETERMINISTIC_LOCAL }
data class UltraAiObservation(
    val gamePackage: String,
    val activeProfileId: String,
    val batteryPercent: Int? = null,
    val thermalLabel: String? = null,
    val refreshRateHz: Float? = null,
    val latencyMs: Int? = null,\n    val thermalStatus: Int? = null,\n    val thermalHeadroom: Float? = null,\n    val sessionActive: Boolean = false\n) {\n    fun sanitized(): UltraAiObservation = copy(\n        batteryPercent = batteryPercent?.takeIf { it in 0..100 },\n        refreshRateHz = refreshRateHz?.takeIf { it.isFinite() && it > 0f },\n        latencyMs = latencyMs?.takeIf { it >= 0 },\n        thermalStatus = thermalStatus?.takeIf { it >= 0 },\n        thermalHeadroom = thermalHeadroom?.takeIf { it.isFinite() && it >= 0f }\n    )\n\n    companion object {\n        fun from(context: GameHubAiContext): UltraAiObservation = UltraAiObservation(\n            gamePackage = context.selectedGamePackage.orEmpty(),\n            activeProfileId = context.selectedProfile.name,\n            batteryPercent = context.batteryPercent,\n            thermalStatus = context.thermalStatus,\n            thermalHeadroom = context.thermalHeadroom,\n            refreshRateHz = context.refreshRateHz,\n            latencyMs = context.networkLatencyMs?.takeIf { it in 0..Int.MAX_VALUE.toLong() }?.toInt(),\n            sessionActive = context.sessionActive\n        ).sanitized()\n    }\n}

data class UltraAiFeedbackSnapshot(
    val acceptedProfileIds: Set<String> = emptySet(),
    val rejectedProfileIds: Set<String> = emptySet()
)

data class UltraAiMemorySignal(val text: String, val provenance: UltraMemoryProvenance) {\n    companion object {\n        fun from(recall: UltraMemoryRecall): UltraAiMemorySignal = UltraAiMemorySignal(\n            text = recall.record.text,\n            provenance = recall.provenance\n        )\n    }\n}

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

class UltraAiCore2(private val recommender: UltraAiRecommender?) {
    fun evaluate(
        observation: UltraAiObservation,
        feedback: UltraAiFeedbackSnapshot,
        memories: List<UltraAiMemorySignal>
    ): UltraAiCoreResult {
        val recommendation = recommender?.recommend(observation, feedback)
            ?: deterministicRecommendation(observation, feedback)
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
