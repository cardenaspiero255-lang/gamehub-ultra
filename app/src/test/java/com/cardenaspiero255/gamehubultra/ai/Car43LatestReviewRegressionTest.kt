package com.cardenaspiero255.gamehubultra.ai

import com.cardenaspiero255.gamehubultra.domain.OptimizationFeedbackDecision
import com.cardenaspiero255.gamehubultra.domain.OptimizationObservation
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.platform.DeviceInfo
import com.cardenaspiero255.gamehubultra.voice.VoiceOptimizationFeedbackContext
import kotlin.test.Test
import kotlin.test.assertEquals

class Car43LatestReviewRegressionTest {
    private val healthyContext = GameHubAiContext(
        selectedGamePackage = "com.example.game",
        sustainedPerformanceSupported = true,
        cpuCores = 8,
        totalRamMb = 8192,
        gpuAvailable = true,
        thermalStatus = 0,
        thermalHeadroom = 0.35f,
        batteryPercent = 90,
        charging = true,
        refreshRateHz = 120f,
        networkValidated = true,
        networkLatencyMs = 35L,
        downstreamBandwidthKbps = 100_000L,
        storageFreePercent = 45,
        inputDeviceCount = 1,
        selectedProfile = PerformanceProfile.BALANCED,
        sessionActive = false,
        optimizationObservations = listOf(
            OptimizationObservation(
                contextKey = "ctx",
                profile = PerformanceProfile.X4,
                feedbackDecision = OptimizationFeedbackDecision.REJECTED,
                timestampMillis = 1L
            )
        )
    )

    @Test
    fun `partial gpu data keeps fixed two-part driver fingerprint`() {
        val vendorOnly = DeviceInfo(
            manufacturer = "Test", model = "Device", androidVersion = "14", sdkInt = 34,
            supportedAbis = emptyList(), cpuModel = "cpu", cpuCores = 8, totalRamMb = 8192,
            gpuVendor = "ARM", gpuRenderer = null
        )
        val rendererOnly = vendorOnly.copy(gpuVendor = null, gpuRenderer = "Mali")

        assertEquals(
            "ARM|",
            VoiceOptimizationFeedbackContext.buildContextKey(vendorOnly, "game", "1", null)
                .driverFingerprint
        )
        assertEquals(
            "|Mali",
            VoiceOptimizationFeedbackContext.buildContextKey(rendererOnly, "game", "1", null)
                .driverFingerprint
        )
    }

    @Test
    fun `stable economic cost concepts remain general knowledge`() {
        listOf(
            "what is opportunity cost?",
            "what is marginal cost?",
            "what does opportunity cost mean?",
            "what does sunk cost mean?",
            "what does marginal cost represent in economics?",
            "what is the cost of goods sold?"
        ).forEach { question ->
            val request = UltraGeneralQueryRouter.classify(question)
            assertEquals(UltraGeneralQueryKind.GENERAL_KNOWLEDGE, request.kind, question)
            assertEquals(UltraVerificationMode.OPTIONAL, request.verificationMode, question)
        }
    }

    @Test
    fun `unambiguous price questions remain fresh`() {
        listOf(
            "What does an iPhone cost?",
            "What is the cost of an iPhone?",
            "What is the cost for a laptop?",
            "What is the price of an iPhone?",
            "How much is an iPhone?",
            "¿Cuánto cuesta un iPhone?"
        ).forEach { question ->
            val request = UltraGeneralQueryRouter.classify(question)
            assertEquals(UltraGeneralQueryKind.CURRENT_DATA, request.kind, question)
            assertEquals(UltraVerificationMode.REQUIRED, request.verificationMode, question)
        }
    }

    @Test
    fun `feedback recovery requires gaming recommendation intent for mode and profile`() {
        val adapter = object : LocalAiModelAdapter {
            override fun isAvailable() = true
            override fun advise(
                question: String,
                context: GameHubAiContext
            ): LocalAiActionCandidate? = null
            override fun chat(
                message: String,
                context: GameHubAiContext,
                conversation: List<String>
            ): String = "Respuesta estable del modelo."
        }

        listOf(
            "What is profile likelihood?",
            "What is the mode of this distribution?",
            "Ultra, what is profile likelihood?",
            "Ultra, what is the mode of this distribution?",
            "Which profile should I use for LinkedIn?",
            "Which mode should I use for accessibility?"
        ).forEach { question ->
            assertEquals(
                "Respuesta estable del modelo.",
                GameHubAiAdvisor(adapter).chat(question, healthyContext),
                question
            )
        }
    }

    @Test
    fun `feedback recovery does not treat modern as mode`() {
        val adapter = object : LocalAiModelAdapter {
            override fun isAvailable() = true
            override fun advise(
                question: String,
                context: GameHubAiContext
            ): LocalAiActionCandidate? = null
            override fun chat(
                message: String,
                context: GameHubAiContext,
                conversation: List<String>
            ): String = "El arte moderno abarca movimientos artísticos de la era moderna."
        }

        val answer = GameHubAiAdvisor(adapter).chat(
            message = "What is modern art?",
            context = healthyContext
        )

        assertEquals(
            "El arte moderno abarca movimientos artísticos de la era moderna.",
            answer
        )
    }
    @Test
    fun `GameHub branded profile requests remain feedback aware`() {
        val adapter = object : LocalAiModelAdapter {
            override fun isAvailable() = true
            override fun advise(
                question: String,
                context: GameHubAiContext
            ) = LocalAiActionCandidate(AiActionAllowlist.PROFILE_X4)

            override fun chat(
                message: String,
                context: GameHubAiContext,
                conversation: List<String>
            ): String = "Te recomiendo X4."
        }
        val context = healthyContext.copy(
            selectedProfile = PerformanceProfile.BALANCED,
            optimizationObservations = listOf(
                OptimizationObservation(
                    contextKey = "ctx",
                    profile = PerformanceProfile.X4,
                    feedbackDecision = OptimizationFeedbackDecision.REJECTED,
                    timestampMillis = 2L
                ),
                OptimizationObservation(
                    contextKey = "ctx",
                    profile = PerformanceProfile.X4,
                    feedbackDecision = OptimizationFeedbackDecision.REJECTED,
                    timestampMillis = 1L
                )
            )
        )

        listOf(
            "GameHub, which profile should I use?",
            "Which GameHub Ultra profile should I use?"
        ).forEach { question ->
            val answer = GameHubAiAdvisor(adapter).chat(question, context)
            kotlin.test.assertFalse(answer.contains("recomiendo X4", ignoreCase = true), question)
            kotlin.test.assertTrue(answer.contains("balance", ignoreCase = true), question)
        }
    }

}
