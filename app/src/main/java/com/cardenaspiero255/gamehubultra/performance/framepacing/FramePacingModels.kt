package com.cardenaspiero255.gamehubultra.performance.framepacing

enum class RefreshStability {
    STABLE,
    MINOR_VARIANCE,
    UNSTABLE,
    INSUFFICIENT_DATA,
    RECOVERING,
    DEGRADING
}

enum class RefreshVerification {
    REQUESTED,
    OBSERVED,
    VERIFIED,
    NOT_APPLIED,
    UNVERIFIABLE
}

enum class FrameDataAvailability {
    AVAILABLE,
    PACING_UNAVAILABLE
}

data class RefreshSample(
    val timestampMs: Long,
    val refreshHz: Float
)

data class FrameTimingSample(
    val timestampMs: Long,
    val frameTimeMs: Float
)

/**
 * observedActive must originate from a verifiable vendor/platform integration.
 * It must never be inferred merely from refresh rate or frame-time presence.
 */
data class InterpolationState(
    val requested: Boolean,
    val capabilityVerified: Boolean,
    val observedActive: Boolean
)

data class FramePacingAssessment(
    val targetHz: Int?,
    val observedRefreshHz: Float?,
    val ewmaRefreshHz: Float?,
    val pacingAvailability: FrameDataAvailability,
    val stability: RefreshStability,
    val varianceHz: Float,
    val stdDevHz: Float,
    val jitterMs: Float?,
    val sampleCountSlow: Int,
    val sampleCountFast: Int,
    val confidenceSlow: Float,
    val confidenceCombined: Float,
    val evidence: List<String>,
    val explanation: String,
    val recommendedRefreshHz: Int?,
    val verification: RefreshVerification,
    val interpolationVerified: Boolean
)

data class FramePacingAdaptiveSignal(
    val stability: RefreshStability,
    val verification: RefreshVerification,
    val recommendedHz: Int?,
    val confidence: Float,
    val shouldNotify: Boolean
)
