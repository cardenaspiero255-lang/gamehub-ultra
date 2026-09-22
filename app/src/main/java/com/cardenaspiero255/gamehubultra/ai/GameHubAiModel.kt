package com.cardenaspiero255.gamehubultra.ai

import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile

data class GameHubAiContext(
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
    val title: String,
    val explanation: String,
    val suggestedProfile: PerformanceProfile?,
    val localModelUsed: Boolean,
    val fallbackUsed: Boolean
)

data class LocalAiActionCandidate(
    val action: String,
    val argument: String? = null
)

interface LocalAiModelAdapter {
    fun isAvailable(): Boolean

    fun advise(
        question: String,
        context: GameHubAiContext
    ): LocalAiActionCandidate?
}
