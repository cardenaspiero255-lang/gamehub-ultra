package com.cardenaspiero255.gamehubultra.performance.framepacing

/**
 * Calibratable policy for the recovered CAR-50 Frame Pacing & Refresh Intelligence.
 *
 * Defaults are conservative initial heuristics for GameHub Ultra, not universal Android
 * display/game specifications. Slow-path defaults are aligned with the existing 15 s
 * SessionCoach sampling cadence; fast-path defaults only apply when legitimate frame timings
 * are supplied by a supported source.
 */
data class FramePacingPolicy(
    val minSamplesSlow: Int = 5,
    val windowSlowMs: Long = 120_000L,
    val minSamplesFast: Int = 20,
    val windowFastMs: Long = 2_000L,
    val minRefreshHz: Float = 10f,
    val maxRefreshHz: Float = 500f,
    val maxFrameTimeMs: Float = 1_000f,
    val noiseToleranceHz: Float = 2f,
    val stdDevAllowedHz: Float = 3f,
    val maxTransitionsForUnstable: Int = 4,
    val transitionMultiplier: Float = 2.5f,
    val targetObservedToleranceHz: Float = 3f,
    val notAppliedThresholdHz: Float = 20f,
    val minStabilityRatio: Float = 0.75f,
    val recoverySamples: Int = 5,
    val minRecoveryStableSamples: Int = 3,
    val recoveryHeadRatio: Float = 0.85f,
    val recoveryTailBoost: Float = 1.10f,
    val degradingMinSamples: Int = 6,
    val degradingRatio: Float = 0.85f,
    val degradingDescendingRatio: Float = 0.60f,
    val minConfidence: Float = 0.60f,
    val ewmaAlpha: Float = 0.25f,
    val jitterThresholdMs: Float = 8f,
    val maxStdForStable: Float = 2.5f,
    val meanToleranceHz: Float = 5f,
    val thermalSafeHz: Int = 90,
    val confidenceSampleWeight: Float = 0.40f,
    val confidenceStabilityWeight: Float = 0.35f,
    val confidenceSpreadWeight: Float = 0.25f,
    val confidenceSpreadMultiplier: Float = 4f,
    val fastConfidenceWeight: Float = 0.20f
) {
    init {
        require(minSamplesSlow >= 3)
        require(windowSlowMs >= 30_000L)
        require(minSamplesFast >= 5)
        require(windowFastMs >= 500L)
        require(minRefreshHz > 0f && minRefreshHz.isFinite())
        require(maxRefreshHz > minRefreshHz && maxRefreshHz.isFinite())
        require(maxFrameTimeMs > 0f && maxFrameTimeMs.isFinite())
        require(noiseToleranceHz in 0.1f..10f)
        require(stdDevAllowedHz in 0.1f..20f)
        require(maxTransitionsForUnstable >= 1)
        require(transitionMultiplier in 1f..5f)
        require(targetObservedToleranceHz in 0f..10f)
        require(notAppliedThresholdHz in 5f..50f)
        require(notAppliedThresholdHz > targetObservedToleranceHz) {
            "histeresis: notAppliedThresholdHz > targetObservedToleranceHz"
        }
        require(minStabilityRatio in 0f..1f)
        require(recoverySamples >= 2)
        require(minRecoveryStableSamples in 2..recoverySamples)
        require(recoveryHeadRatio in 0f..1f)
        require(recoveryTailBoost in 1f..2f)
        require(degradingMinSamples >= 4)
        require(degradingRatio in 0f..1f)
        require(degradingDescendingRatio in 0f..1f)
        require(minConfidence in 0f..1f)
        require(ewmaAlpha in 0.05f..0.9f)
        require(jitterThresholdMs >= 1f)
        require(maxStdForStable in 0.5f..10f)
        require(meanToleranceHz >= 0f)
        require(thermalSafeHz > 0)
        require(confidenceSampleWeight in 0f..1f)
        require(confidenceStabilityWeight in 0f..1f)
        require(confidenceSpreadWeight in 0f..1f)
        require(
            kotlin.math.abs(
                confidenceSampleWeight + confidenceStabilityWeight + confidenceSpreadWeight - 1f
            ) < 0.001f
        ) { "slow confidence weights must sum to 1" }
        require(confidenceSpreadMultiplier > 0f)
        require(fastConfidenceWeight in 0f..1f)
    }
}
