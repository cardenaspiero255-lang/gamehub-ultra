package com.cardenaspiero255.gamehubultra.performance.framepacing

import kotlin.math.abs

/**
 * Política calibrable del CAR-50 recuperado.
 *
 * Los valores por defecto son heurísticas iniciales conservadoras para GameHub Ultra,
 * no especificaciones universales de Android, del panel ni del motor de un juego.
 * Los umbrales viven aquí para poder calibrarlos sin reescribir el motor.
 */
data class FramePacingPolicy(
    val minSamplesSlow: Int = 5,
    val sampleIntervalSlowMs: Long = 15_000L,
    val windowSlowMs: Long = 120_000L,
    val minSamplesFast: Int = 20,
    val windowFastMs: Long = 2_000L,
    val minSamplesForTrend: Int = 6,
    val noiseToleranceHz: Float = 2f,
    val stdDevAllowedHz: Float = 3f,
    val maxTransitionsForUnstable: Int = 4,
    val transitionMultiplier: Float = 2.5f,
    val targetObservedToleranceHz: Float = 3f,
    val notAppliedThresholdHz: Float = 20f,
    val minStabilityRatio: Float = 0.75f,
    val recoverySamples: Int = 5,
    val recoveryHeadRatio: Float = 0.85f,
    val recoveryTailBoost: Float = 1.1f,
    val degradingRatio: Float = 0.85f,
    val degradingDescendingRatio: Float = 0.6f,
    val minConfidence: Float = 0.6f,
    val ewmaAlpha: Float = 0.25f,
    val jitterThresholdMs: Float = 8f,
    val maxStdForStable: Float = 2.5f,
    val meanToleranceHz: Float = 5f,
    val conservativeRefreshCapHz: Int = 90,
    val refreshMinHz: Float = 10f,
    val refreshMaxHz: Float = 500f,
    val frameTimeMaxMs: Float = 1_000f,
    val confidenceSampleSaturationMultiplier: Float = 2f,
    val confidenceSlowStdDevDivisor: Float = 4f,
    val confidenceStableRatioWeight: Float = 0.5f,
    val confidenceJitterBase: Float = 0.85f,
    val confidenceJitterDivisor: Float = 3f,
    val confidenceCombinedSlowWeight: Float = 0.7f,
    val confidenceCombinedFastWeight: Float = 0.3f,
    val confidenceMinorBase: Float = 0.6f,
    val confidenceMinorVarWeight: Float = 0.4f,
    val confidenceUnstableBase: Float = 0.5f,
    val confidenceUnstableTransWeight: Float = 0.5f,
    val confidenceDegradingBase: Float = 0.4f,
    val confidenceDegradingDescWeight: Float = 0.3f,
    val confidenceDegradingDiffWeight: Float = 0.3f,
    val confidenceRecoveringBase: Float = 0.4f,
    val confidenceRecoveringDiffWeight: Float = 0.3f,
    val confidenceRecoveringStabWeight: Float = 0.3f,
    val confidenceStableStdCap: Float = 0.7f
) {
    init {
        require(minSamplesSlow >= 3)
        require(minSamplesForTrend >= 6)
        require(sampleIntervalSlowMs >= 1_000L)
        require(windowSlowMs >= 30_000L)
        require(windowSlowMs >= (minSamplesSlow - 1L) * sampleIntervalSlowMs) {
            "windowSlowMs no permite reunir minSamplesSlow con la cadencia configurada."
        }
        require(windowSlowMs >= (minSamplesForTrend - 1L) * sampleIntervalSlowMs) {
            "minSamplesForTrend no cabe físicamente en windowSlowMs."
        }
        require(minSamplesFast >= 5)
        require(windowFastMs >= 500L)

        require(noiseToleranceHz.isFinite() && noiseToleranceHz in 0.1f..10f)
        require(stdDevAllowedHz.isFinite() && stdDevAllowedHz in 0.1f..20f)
        require(maxTransitionsForUnstable >= 1)
        require(transitionMultiplier.isFinite() && transitionMultiplier in 1f..5f)
        require(
            targetObservedToleranceHz.isFinite() &&
                targetObservedToleranceHz in 0f..10f
        )
        require(
            notAppliedThresholdHz.isFinite() &&
                notAppliedThresholdHz in 5f..50f
        )
        require(notAppliedThresholdHz > targetObservedToleranceHz)
        require(minStabilityRatio.isFinite() && minStabilityRatio in 0f..1f)

        require(recoverySamples >= 2)
        require(recoveryHeadRatio.isFinite() && recoveryHeadRatio in 0f..1f)
        require(recoveryTailBoost.isFinite() && recoveryTailBoost in 1f..2f)
        require(degradingRatio.isFinite() && degradingRatio in 0f..1f)
        require(
            degradingDescendingRatio.isFinite() &&
                degradingDescendingRatio in 0f..1f
        )

        require(minConfidence.isFinite() && minConfidence in 0f..1f)
        require(ewmaAlpha.isFinite() && ewmaAlpha in 0.05f..0.9f)
        require(jitterThresholdMs.isFinite() && jitterThresholdMs >= 1f)
        require(maxStdForStable.isFinite() && maxStdForStable in 0.5f..10f)
        require(meanToleranceHz.isFinite() && meanToleranceHz in 0f..20f)
        require(conservativeRefreshCapHz in 30..240)

        require(refreshMinHz.isFinite() && refreshMinHz in 1f..100f)
        require(refreshMaxHz.isFinite() && refreshMaxHz in 100f..1_000f)
        require(refreshMaxHz > refreshMinHz)
        require(frameTimeMaxMs.isFinite() && frameTimeMaxMs in 100f..5_000f)

        require(
            confidenceSampleSaturationMultiplier.isFinite() &&
                confidenceSampleSaturationMultiplier in 1f..10f
        )
        require(
            confidenceSlowStdDevDivisor.isFinite() &&
                confidenceSlowStdDevDivisor in 1f..10f
        )
        require(
            confidenceStableRatioWeight.isFinite() &&
                confidenceStableRatioWeight in 0f..1f
        )
        require(confidenceJitterBase.isFinite() && confidenceJitterBase in 0f..1f)
        require(
            confidenceJitterDivisor.isFinite() &&
                confidenceJitterDivisor in 1f..10f
        )
        require(
            confidenceCombinedSlowWeight.isFinite() &&
                confidenceCombinedSlowWeight in 0f..1f
        )
        require(
            confidenceCombinedFastWeight.isFinite() &&
                confidenceCombinedFastWeight in 0f..1f
        )
        require(
            abs(
                confidenceCombinedSlowWeight +
                    confidenceCombinedFastWeight -
                    1f
            ) <= WEIGHT_EPSILON
        )

        require(confidenceMinorBase.isFinite() && confidenceMinorBase in 0f..1f)
        require(
            confidenceMinorVarWeight.isFinite() &&
                confidenceMinorVarWeight in 0f..1f
        )
        require(
            abs(confidenceMinorBase + confidenceMinorVarWeight - 1f) <=
                WEIGHT_EPSILON
        )

        require(
            confidenceUnstableBase.isFinite() &&
                confidenceUnstableBase in 0f..1f
        )
        require(
            confidenceUnstableTransWeight.isFinite() &&
                confidenceUnstableTransWeight in 0f..1f
        )
        require(
            abs(
                confidenceUnstableBase +
                    confidenceUnstableTransWeight -
                    1f
            ) <= WEIGHT_EPSILON
        )

        require(
            confidenceDegradingBase.isFinite() &&
                confidenceDegradingBase in 0f..1f
        )
        require(
            confidenceDegradingDescWeight.isFinite() &&
                confidenceDegradingDescWeight in 0f..1f
        )
        require(
            confidenceDegradingDiffWeight.isFinite() &&
                confidenceDegradingDiffWeight in 0f..1f
        )
        require(
            abs(
                confidenceDegradingBase +
                    confidenceDegradingDescWeight +
                    confidenceDegradingDiffWeight -
                    1f
            ) <= WEIGHT_EPSILON
        )

        require(
            confidenceRecoveringBase.isFinite() &&
                confidenceRecoveringBase in 0f..1f
        )
        require(
            confidenceRecoveringDiffWeight.isFinite() &&
                confidenceRecoveringDiffWeight in 0f..1f
        )
        require(
            confidenceRecoveringStabWeight.isFinite() &&
                confidenceRecoveringStabWeight in 0f..1f
        )
        require(
            abs(
                confidenceRecoveringBase +
                    confidenceRecoveringDiffWeight +
                    confidenceRecoveringStabWeight -
                    1f
            ) <= WEIGHT_EPSILON
        )

        require(
            confidenceStableStdCap.isFinite() &&
                confidenceStableStdCap in 0f..1f
        )
    }

    private companion object {
        const val WEIGHT_EPSILON = 0.001f
    }
}
