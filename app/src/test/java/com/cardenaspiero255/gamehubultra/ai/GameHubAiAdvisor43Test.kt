package com.cardenaspiero255.gamehubultra.ai

import com.cardenaspiero255.gamehubultra.domain.OptimizationFeedbackDecision
import com.cardenaspiero255.gamehubultra.domain.OptimizationObservation
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

    @Test
    fun `feedback recovery cannot override hot-device safety`() {
        val hotContext = contextWithPoorX4History.copy(
            thermalStatus = 4,
            thermalHeadroom = 0.90f,
            selectedProfile = PerformanceProfile.BALANCED,
            optimizationObservations = listOf(
                OptimizationObservation(
                    contextKey = "device|game",
                    profile = PerformanceProfile.BALANCED,
                    timestampMillis = 4L,
                    feedbackDecision = OptimizationFeedbackDecision.REJECTED
                ),
                OptimizationObservation(
                    contextKey = "device|game",
                    profile = PerformanceProfile.BALANCED,
                    timestampMillis = 3L,
                    feedbackDecision = OptimizationFeedbackDecision.REJECTED
                ),
                OptimizationObservation(
                    contextKey = "device|game",
                    profile = PerformanceProfile.X4,
                    timestampMillis = 2L,
                    feedbackDecision = OptimizationFeedbackDecision.ACCEPTED
                )
            )
        )
        val adapter = object : LocalAiModelAdapter {
            override fun isAvailable() = true
            override fun advise(question: String, context: GameHubAiContext) =
                LocalAiActionCandidate(AiActionAllowlist.PROFILE_BALANCED)
        }

        val result = GameHubAiAdvisor(modelAdapter = adapter).advise(
            "¿qué perfil me recomiendas?",
            hotContext
        )

        assertEquals(PerformanceProfile.BALANCED, result.suggestedProfile)
        assertEquals(AiAdviceReason.THERMAL, result.reason)
    }

    @Test
    fun `recovery preserves current safe profile as fallback`() {
        val context = contextWithPoorX4History.copy(
            selectedProfile = PerformanceProfile.FRAME_INTERPOLATION,
            optimizationObservations = listOf(
                OptimizationObservation(
                    contextKey = "device|game",
                    profile = PerformanceProfile.X4,
                    timestampMillis = 2L,
                    feedbackDecision = OptimizationFeedbackDecision.REJECTED
                ),
                OptimizationObservation(
                    contextKey = "device|game",
                    profile = PerformanceProfile.X4,
                    timestampMillis = 1L,
                    feedbackDecision = OptimizationFeedbackDecision.REJECTED
                )
            )
        )
        val adapter = object : LocalAiModelAdapter {
            override fun isAvailable() = true
            override fun advise(question: String, context: GameHubAiContext) =
                LocalAiActionCandidate(AiActionAllowlist.PROFILE_X4)
        }

        val result = GameHubAiAdvisor(modelAdapter = adapter).advise(
            "¿qué perfil me recomiendas?",
            context
        )

        assertEquals(PerformanceProfile.FRAME_INTERPOLATION, result.suggestedProfile)
    }

    @Test
    fun `local chat cannot bypass rejected profile recovery`() {
        val adapter = object : LocalAiModelAdapter {
            override fun isAvailable() = true
            override fun advise(question: String, context: GameHubAiContext) =
                LocalAiActionCandidate(AiActionAllowlist.PROFILE_X4)
            override fun chat(
                message: String,
                context: GameHubAiContext,
                conversation: List<String>
            ) = "Te recomiendo X4."
        }

        val answer = GameHubAiAdvisor(modelAdapter = adapter).chat(
            "¿qué perfil me recomiendas?",
            contextWithPoorX4History,
            emptyList()
        )

        assertTrue(answer.contains("balance", ignoreCase = true))
        assertFalse(answer.contains("recomiendo X4", ignoreCase = true))
    }

    @Test
    fun `recovery explanation is omitted when safety blocks the recovered profile`() {
        val hotContext = contextWithPoorX4History.copy(
            thermalStatus = 4,
            thermalHeadroom = 0.90f,
            selectedProfile = PerformanceProfile.BALANCED,
            optimizationObservations = listOf(
                OptimizationObservation(
                    contextKey = "device|game",
                    profile = PerformanceProfile.BALANCED,
                    timestampMillis = 3L,
                    feedbackDecision = OptimizationFeedbackDecision.REJECTED
                ),
                OptimizationObservation(
                    contextKey = "device|game",
                    profile = PerformanceProfile.BALANCED,
                    timestampMillis = 2L,
                    feedbackDecision = OptimizationFeedbackDecision.REJECTED
                ),
                OptimizationObservation(
                    contextKey = "device|game",
                    profile = PerformanceProfile.X4,
                    timestampMillis = 1L,
                    feedbackDecision = OptimizationFeedbackDecision.ACCEPTED
                )
            )
        )

        val result = GameHubAiAdvisor().advise("perfil", hotContext)

        assertEquals(PerformanceProfile.BALANCED, result.suggestedProfile)
        assertNull(result.recoveryExplanation)
    }

    @Test
    fun `single rejected proposed profile keeps recovery explanation`() {
        val context = contextWithPoorX4History.copy(
            selectedProfile = PerformanceProfile.BALANCED,
            optimizationObservations = listOf(
                OptimizationObservation(
                    contextKey = "device|game",
                    profile = PerformanceProfile.X4,
                    timestampMillis = 1L,
                    feedbackDecision = OptimizationFeedbackDecision.REJECTED
                )
            )
        )

        val result = GameHubAiAdvisor().advise(
            "¿qué perfil me recomiendas?",
            context
        )

        assertEquals(PerformanceProfile.X4, result.suggestedProfile)
        assertNotNull(result.recoveryExplanation)
    }

    @Test
    fun `local model feedback is evaluated against proposed profile`() {
        val context = contextWithPoorX4History.copy(
            selectedProfile = PerformanceProfile.BALANCED,
            optimizationObservations = listOf(
                OptimizationObservation(
                    contextKey = "device|game",
                    profile = PerformanceProfile.X4,
                    timestampMillis = 2L,
                    feedbackDecision = OptimizationFeedbackDecision.REJECTED
                ),
                OptimizationObservation(
                    contextKey = "device|game",
                    profile = PerformanceProfile.X4,
                    timestampMillis = 1L,
                    feedbackDecision = OptimizationFeedbackDecision.REJECTED
                )
            )
        )
        val adapter = object : LocalAiModelAdapter {
            override fun isAvailable() = true
            override fun advise(question: String, context: GameHubAiContext) =
                LocalAiActionCandidate(AiActionAllowlist.PROFILE_X4)
        }

        val result = GameHubAiAdvisor(modelAdapter = adapter).advise(
            "¿qué perfil me recomiendas?",
            context
        )

        assertEquals(PerformanceProfile.BALANCED, result.suggestedProfile)
        assertNotNull(result.recoveryExplanation)
    }

}
