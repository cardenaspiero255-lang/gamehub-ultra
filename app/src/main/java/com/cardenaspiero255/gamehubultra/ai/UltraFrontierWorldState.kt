package com.cardenaspiero255.gamehubultra.ai

import com.cardenaspiero255.gamehubultra.domain.AdaptiveDecision
import com.cardenaspiero255.gamehubultra.domain.BatteryAwareGamingEngine
import com.cardenaspiero255.gamehubultra.domain.SessionCoachSnapshot
import com.cardenaspiero255.gamehubultra.domain.ThermalPredictionEngine
import com.cardenaspiero255.gamehubultra.domain.BatteryGamingAssessment
import com.cardenaspiero255.gamehubultra.domain.BatteryGamingRecommendation
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.domain.ThermalPrediction
import com.cardenaspiero255.gamehubultra.domain.ThermalRisk
import com.cardenaspiero255.gamehubultra.domain.ThermalTrend

data class UltraFrontierWorldState(
    val selectedGamePackage: String?,
    val sessionActive: Boolean,
    val selectedProfile: PerformanceProfile,
    val networkValidated: Boolean,
    val networkLatencyMs: Long?,
    val batteryPercent: Int?,
    val charging: Boolean,
    val thermalStatus: Int?,
    val thermalHeadroom: Float?,
    val thermalTrend: ThermalTrend?,
    val thermalRisk: ThermalRisk?,
    val thermalConfidence: Float?,
    val batteryRecommendation: BatteryGamingRecommendation?,
    val preventAggressiveProfiles: Boolean,
    val adaptiveScore: Int?,
    val timestampMillis: Long
) {
    companion object {
        fun from(
            context: GameHubAiContext,
            thermalPrediction: ThermalPrediction? = null,
            batteryAssessment: BatteryGamingAssessment? = null,
            adaptiveDecision: AdaptiveDecision? = null,
            nowMillis: Long = System.currentTimeMillis()
        ): UltraFrontierWorldState =
            UltraFrontierWorldState(
                selectedGamePackage = context.selectedGamePackage,
                sessionActive = context.sessionActive,
                selectedProfile =
                    adaptiveDecision?.profile ?: context.selectedProfile,
                networkValidated = context.networkValidated,
                networkLatencyMs = context.networkLatencyMs,
                batteryPercent =
                    batteryAssessment?.currentPercent ?: context.batteryPercent,
                charging =
                    batteryAssessment?.charging ?: context.charging,
                thermalStatus = context.thermalStatus,
                thermalHeadroom =
                    thermalPrediction?.latestMeasuredHeadroom
                        ?: context.thermalHeadroom,
                thermalTrend = thermalPrediction?.trend,
                thermalRisk = thermalPrediction?.risk,
                thermalConfidence = thermalPrediction?.confidence,
                batteryRecommendation = batteryAssessment?.recommendation,
                preventAggressiveProfiles =
                    batteryAssessment?.preventAggressiveProfiles ?: false,
                adaptiveScore = adaptiveDecision?.score,
                timestampMillis = nowMillis
            )
    }
}

object UltraFrontierWorldStateUpdater {
    fun update(
        context: GameHubAiContext,
        sessionSamples: List<SessionCoachSnapshot>,
        adaptiveDecision: AdaptiveDecision?,
        nowMillis: Long = System.currentTimeMillis()
    ): UltraFrontierWorldState {
        val orderedSamples = sessionSamples
            .asSequence()
            .filter { it.timestampMillis >= 0L }
            .sortedBy { it.timestampMillis }
            .toList()
        val thermalPrediction = orderedSamples
            .takeIf(List<SessionCoachSnapshot>::isNotEmpty)
            ?.let { ThermalPredictionEngine().predict(it) }
        val batteryAssessment = orderedSamples
            .takeIf(List<SessionCoachSnapshot>::isNotEmpty)
            ?.let { BatteryAwareGamingEngine().assess(it) }

        return UltraFrontierWorldState.from(
            context = context,
            thermalPrediction = thermalPrediction,
            batteryAssessment = batteryAssessment,
            adaptiveDecision = adaptiveDecision,
            nowMillis = nowMillis
        ).also(UltraFrontierWorldStateRegistry::update)
    }
}

object UltraFrontierWorldStateRegistry {
    @Volatile
    private var current: UltraFrontierWorldState? = null

    fun update(state: UltraFrontierWorldState) {
        current = state
    }

    fun update(
        context: GameHubAiContext,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        val base = UltraFrontierWorldState.from(
            context = context,
            nowMillis = nowMillis
        )
        val previous = current
        val previousAge = previous?.let {
            nowMillis - it.timestampMillis
        }
        val canReuseAdvanced =
            previous != null &&
                previous.selectedGamePackage == context.selectedGamePackage &&
                previousAge != null &&
                previousAge >= 0L &&
                previousAge <= ADVANCED_SIGNAL_MAX_AGE_MILLIS

        current = if (canReuseAdvanced) {
            base.copy(
                thermalTrend = previous?.thermalTrend,
                thermalRisk = previous?.thermalRisk,
                thermalConfidence = previous?.thermalConfidence,
                batteryRecommendation = previous?.batteryRecommendation,
                preventAggressiveProfiles =
                    previous?.preventAggressiveProfiles ?: false,
                adaptiveScore = previous?.adaptiveScore
            )
        } else {
            base
        }
    }

    fun snapshot(): UltraFrontierWorldState? = current

    fun snapshotForPlanning(
        nowMillis: Long = System.currentTimeMillis()
    ): UltraFrontierWorldState? {
        val state = current ?: return null
        val age = nowMillis - state.timestampMillis
        if (age < 0L || age > ADVANCED_SIGNAL_MAX_AGE_MILLIS) {
            return null
        }
        return state
    }

    fun clear() {
        current = null
    }

    private const val ADVANCED_SIGNAL_MAX_AGE_MILLIS = 2 * 60_000L
}
