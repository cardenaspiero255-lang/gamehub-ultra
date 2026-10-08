package com.cardenaspiero255.gamehubultra.performance.framepacing

enum class RefreshStability {
    STABLE,
    MINOR_VARIANCE,
    UNSTABLE,
    INSUFFICIENT_DATA
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
    FRAME_DATA_UNAVAILABLE,
    PACING_UNAVAILABLE
}

data class RefreshSample(
    val timestampMs: Long,
    val refreshHz: Float
)

data class FrameTimingSample(
    val timestampMs: Long,
    val frameDurationMs: Float
)

data class FramePacingAssessment(
    val targetHz: Int?,
    val observedRefreshHz: Float?,
    val observedGameFps: Float?,
    val frameDataAvailability: FrameDataAvailability,
    val stability: RefreshStability,
    val varianceHz: Float,
    val sampleCount: Int,
    val confidence: Float,
    val evidence: List<String>,
    val explanation: String,
    val recommendedRefreshHz: Int?,
    val verification: RefreshVerification,
    val interpolationVerified: Boolean = false,
    val framePacingStable: Boolean? = null,
    val frameTimeStdDevMs: Float? = null
)
