package com.cardenaspiero255.gamehubultra.voice

import com.cardenaspiero255.gamehubultra.ai.GameHubAiContext
import com.cardenaspiero255.gamehubultra.data.GameOptimizationMemoryStateRepository
import com.cardenaspiero255.gamehubultra.data.OptimizationContextKey
import com.cardenaspiero255.gamehubultra.domain.OptimizationFeedbackDecision
import com.cardenaspiero255.gamehubultra.domain.OptimizationObservation
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class VoiceOptimizationFeedbackContextTest {
    @Test
    fun `voice context carries scoped rejected and reverted recommendations`() = runBlocking {
        val key = OptimizationContextKey("device", "game.a", "1", null, "gpu")
        val history = listOf(
            OptimizationObservation(
                contextKey = key.serialized,
                profile = PerformanceProfile.X4,
                feedbackDecision = OptimizationFeedbackDecision.REJECTED,
                timestampMillis = 2L
            ),
            OptimizationObservation(
                contextKey = key.serialized,
                profile = PerformanceProfile.FRAME_INTERPOLATION,
                feedbackDecision = OptimizationFeedbackDecision.REVERTED,
                timestampMillis = 1L
            )
        )
        val repository = object : GameOptimizationMemoryStateRepository {
            override fun observationsFlow(contextKey: OptimizationContextKey): Flow<List<OptimizationObservation>> =
                flowOf(if (contextKey == key) history else emptyList())

            override suspend fun record(
                contextKey: OptimizationContextKey,
                observation: OptimizationObservation
            ) = Unit

            override suspend fun pruneTo(contextKey: OptimizationContextKey) = Unit
            override suspend fun clearAll() = Unit
            override suspend fun clearGame(contextKey: OptimizationContextKey) = Unit
        }
        val base = GameHubAiContext(
            selectedGamePackage = "game.a",
            sustainedPerformanceSupported = true,
            cpuCores = 8,
            totalRamMb = 8192,
            gpuAvailable = true,
            thermalStatus = 0,
            thermalHeadroom = 0.2f,
            batteryPercent = 80,
            charging = false,
            refreshRateHz = 120f,
            networkValidated = true,
            networkLatencyMs = 30L,
            downstreamBandwidthKbps = 100_000L,
            storageFreePercent = 50,
            inputDeviceCount = 1,
            selectedProfile = PerformanceProfile.BALANCED,
            sessionActive = true
        )

        val enriched = VoiceOptimizationFeedbackContext.enrich(base, repository, key)

        assertEquals(history, enriched.optimizationObservations)
    }
}
