package com.cardenaspiero255.gamehubultra.ai

import com.cardenaspiero255.gamehubultra.domain.OptimizationObservation
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile

enum class AiAdviceReason {
    THERMAL,
    LOW_BATTERY,
    LOW_STORAGE,
    NETWORK,
    X4_READY,
    INTERPOLATION,
    BALANCED_GENERAL,
    LOCAL_MODEL_BALANCED,
    LOCAL_MODEL_INTERPOLATION,
    LOCAL_MODEL_X4
}

data class GameHubAiContext(
    val selectedGamePackage: String?,
    val sustainedPerformanceSupported: Boolean,
    val cpuCores: Int,
    val totalRamMb: Int,
    val gpuAvailable: Boolean,
    val thermalStatus: Int?,
    val thermalHeadroom: Float?,
    val batteryPercent: Int?,
    val charging: Boolean,
    val refreshRateHz: Float?,
    val networkValidated: Boolean,
    val networkLatencyMs: Long?,
    val downstreamBandwidthKbps: Long?,
    val storageFreePercent: Int,
    val inputDeviceCount: Int,
    val selectedProfile: PerformanceProfile,
    val sessionActive: Boolean,
    val optimizationObservations: List<OptimizationObservation> = emptyList()
)

data class GameHubAiAdvice(
    val readiness: Int,
    val suggestedProfile: PerformanceProfile,
    val reason: AiAdviceReason,
    val localModelUsed: Boolean,
    val fallbackUsed: Boolean,
    val recoveryExplanation: String? = null
)

data class LocalAiActionCandidate(
    val action: String,
    val argument: String? = null
)

/**
 * Model output is data, never permission to execute anything.
 * Targets are checked against the original user transcript.
 */
data class LocalVoiceIntentCandidate(
    val command: String,
    val argument: String? = null
)

interface LocalAiModelAdapter : AutoCloseable {
    fun isAvailable(): Boolean

    fun advise(
        question: String,
        context: GameHubAiContext
    ): LocalAiActionCandidate?

    /** Optional local language-to-intent classification. */
    fun interpretVoiceIntent(transcript: String): LocalVoiceIntentCandidate? = null

    /** Optional free-form local conversation. */
    fun chat(
        message: String,
        context: GameHubAiContext,
        conversation: List<String> = emptyList()
    ): String? = null

    override fun close() = Unit
}
