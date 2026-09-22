package com.cardenaspiero255.gamehubultra.ai

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
    val sessionActive: Boolean
)

data class GameHubAiAdvice(
    val readiness: Int,
    val suggestedProfile: PerformanceProfile,
    val reason: AiAdviceReason,
    val localModelUsed: Boolean,
    val fallbackUsed: Boolean
)

data class LocalAiActionCandidate(
    val action: String,
    val argument: String? = null
)

interface LocalAiModelAdapter : AutoCloseable {
    fun isAvailable(): Boolean

    fun advise(
        question: String,
        context: GameHubAiContext
    ): LocalAiActionCandidate?

    override fun close() = Unit
}
