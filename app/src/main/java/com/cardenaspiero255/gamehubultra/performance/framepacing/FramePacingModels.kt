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
 * La interpolación solo se considera verificada cuando las tres señales proceden de una
 * integración legítima. CAR-50 nunca la infiere a partir de Hz o frame timings.
 */
data class InterpolationState(
    val requested: Boolean,
    val capabilityVerified: Boolean,
    val observedActive: Boolean
)

/**
 * Resultado explicable del CAR-50.
 *
 * [observedGameFps] solo contiene una medición externa verificada que haya sido entregada
 * explícitamente al motor. Nunca se deriva del refresco de pantalla ni del jitter.
 */
data class FramePacingAssessment(
    val targetHz: Int?,
    val observedRefreshHz: Float?,
    val observedGameFps: Float?,
    val ewmaRefreshHz: Float?,
    val pacingAvailability: FrameDataAvailability,
    val stability: RefreshStability,
    val varianceHz: Float,
    val stdDevHz: Float,
    val jitterMs: Float?,
    val sampleCountSlow: Int,
    val sampleCountFast: Int,
    val confidenceSlow: Float,
    val confidenceFast: Float,
    val confidenceCombined: Float,
    val evidence: List<String>,
    val explanation: String,
    val recommendedRefreshHz: Int?,
    val verification: RefreshVerification,
    val interpolationVerified: Boolean
)

/**
 * Señal tipada para que CAR-47 pueda decidir perfiles sin duplicar su política de acciones.
 */
data class FramePacingAdaptiveSignal(
    val stability: RefreshStability,
    val verification: RefreshVerification,
    val recommendedHz: Int?,
    val confidence: Float,
    val shouldNotify: Boolean
)
