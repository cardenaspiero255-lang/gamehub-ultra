package com.cardenaspiero255.gamehubultra.performance.framepacing

/**
 * Calibratable CAR-50 recovered policy.
 *
 * Defaults are conservative starting heuristics for GameHub Ultra. They are not universal
 * Android display or game-engine specifications and can be tuned without rewriting the engine.
 */
data class FramePacingPolicy(
    val minSamples: Int = 10,
    val windowMs: Long = 5_000L,
    val noiseToleranceHz: Float = 1.5f,
    val varianceAllowedHz: Float = 3.0f,
    val maxTransitionsForUnstable: Int = 3,
    val targetObservedToleranceHz: Float = 2.0f,
    val minStabilityRatio: Float = 0.8f,
    val recoverySamples: Int = 6,
    val minConfidence: Float = 0.6f
) {
    init {
        require(minSamples >= 3)
        require(windowMs >= 1_000L)
        require(noiseToleranceHz >= 0f && noiseToleranceHz.isFinite())
        require(varianceAllowedHz >= 0f && varianceAllowedHz.isFinite())
        require(maxTransitionsForUnstable >= 1)
        require(targetObservedToleranceHz >= 0f && targetObservedToleranceHz.isFinite())
        require(minStabilityRatio in 0f..1f)
        require(recoverySamples >= 2)
        require(minConfidence in 0f..1f)
    }
}
