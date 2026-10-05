package com.cardenaspiero255.gamehubultra.ai

import com.cardenaspiero255.gamehubultra.domain.OptimizationFeedbackDecision
import com.cardenaspiero255.gamehubultra.domain.OptimizationObservation
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GameHubAiAdvisor43Test {
    private val contextWithPoorX4History = GameHubAiContext(
        selectedGamePackage = "com.example.game",
        sustainedPerformanceSupported = true,
        cpuCores = 8,
        totalRamMb = 8192,
        gpuAvailable = true,
        thermalStatus = 0,
        thermalHeadroom = 0.30f,
        batteryPercent = 90,
        charging = true,
        refreshRateHz = 120f,
        networkValidated = true,
        networkLatencyMs = 30L,
        downstreamBandwidthKbps = 100_000L,
        storageFreePercent = 50,
        inputDeviceCount = 1,
        selectedProfile = PerformanceProfile.X4,
        sessionActive = false,
        optimizationObservations = listOf(
            OptimizationObservation(
                contextKey = "device|game",
                profile = PerformanceProfile.X4,
                timestampMillis = 3L,
                feedbackDecision = OptimizationFeedbackDecision.REJECTED
            ),
            OptimizationObservation(
                contextKey = "device|game",
                profile = PerformanceProfile.X4,
                timestampMillis = 2L,
                feedbackDecision = OptimizationFeedbackDecision.REJECTED
            ),
            OptimizationObservation(
                contextKey = "device|game",
                profile = PerformanceProfile.BALANCED,
                timestampMillis = 1L,
                feedbackDecision = OptimizationFeedbackDecision.ACCEPTED
            )
        )
    )

    @Test
    fun `local model recommendation is recovered using persisted CAR42 feedback history`() {
        val adapter = object : LocalAiModelAdapter {
            override fun isAvailable() = true

            override fun advise(
                question: String,
                context: GameHubAiContext
            ) = LocalAiActionCandidate(AiActionAllowlist.PROFILE_X4)
        }

        val result = GameHubAiAdvisor(modelAdapter = adapter).advise(
            "¿qué perfil me recomiendas?",
            contextWithPoorX4History
        )

        assertEquals(PerformanceProfile.BALANCED, result.suggestedProfile)
        assertTrue(result.localModelUsed)
        assertTrue(result.fallbackUsed)
    }

    @Test
    fun `feedback snapshot counts persisted rejected and reverted outcomes`() {
        val snapshot = UltraAiFeedbackSnapshot.fromObservations(
            contextWithPoorX4History.optimizationObservations +
                OptimizationObservation(
                    contextKey = "device|game",
                    profile = PerformanceProfile.FRAME_INTERPOLATION,
                    timestampMillis = 4L,
                    feedbackDecision = OptimizationFeedbackDecision.REVERTED
                )
        )

        assertEquals(2, snapshot.rejectedCount("X4"))
        assertEquals(1, snapshot.revertedCount("FRAME_INTERPOLATION"))
        assertTrue(snapshot.isAccepted("BALANCED"))
    }

    @Test
    fun `chat explains why Ultra changed a repeatedly poor recommendation`() {
        val answer = GameHubAiAdvisor().chat(
            "¿qué perfil me recomiendas?",
            contextWithPoorX4History,
            emptyList()
        )

        assertTrue(answer.contains("Ajusté", ignoreCase = true))
        assertTrue(answer.contains("rendimiento previo", ignoreCase = true))
    }

}
