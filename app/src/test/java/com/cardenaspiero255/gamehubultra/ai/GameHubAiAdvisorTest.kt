package com.cardenaspiero255.gamehubultra.ai

import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.voice.VoiceCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GameHubAiAdvisorTest {
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
        sessionActive = false
    )

    @Test
    fun deterministicFallbackWorksWithoutLocalModel() {
        val result = GameHubAiAdvisor().advise("que modo me recomiendas", healthyContext)

        assertFalse(result.localModelUsed)
        assertTrue(result.fallbackUsed)
        assertEquals(PerformanceProfile.X4, result.suggestedProfile)
        assertEquals(AiAdviceReason.X4_READY, result.reason)
    }

    @Test
    fun thermalOrBatteryPressureFallsBackToBalanced() {
        val advisor = GameHubAiAdvisor()

        assertEquals(
            PerformanceProfile.BALANCED,
            advisor.advise(
                "que modo me recomiendas",
                healthyContext.copy(thermalHeadroom = 0.9f)
            ).suggestedProfile
        )

        assertEquals(
            PerformanceProfile.BALANCED,
            advisor.advise(
                "que modo me recomiendas",
                healthyContext.copy(batteryPercent = 10, charging = false)
            ).suggestedProfile
        )
    }

    @Test
    fun unsupportedX4FallsBackToBalanced() {
        val advisor = GameHubAiAdvisor()
        val result = advisor.advise(
            "que modo me recomiendas",
            healthyContext.copy(sustainedPerformanceSupported = false)
        )

        assertEquals(PerformanceProfile.BALANCED, result.suggestedProfile)
        assertFalse(result.suggestedProfile == PerformanceProfile.X4)
    }

    @Test
    fun voiceResolverOnlyClaimsAiForAdviceQuestions() {
        val resolver = GameHubAiAdvisor().intentResolver()

        assertIs<VoiceCommand.AskAi>(
            resolver.resolve("¿Qué perfil me recomiendas?")
        )
        assertEquals(
            null,
            resolver.resolve("abre Resident Evil 4 Remake")
        )
    }

    @Test
    fun invalidModelCandidateFallsBack() {
        val adapter = object : LocalAiModelAdapter {
            override fun isAvailable() = true

            override fun advise(
                question: String,
                context: GameHubAiContext
            ) = LocalAiActionCandidate("OPEN_URL", "https://example.com")
        }

        val result = GameHubAiAdvisor(adapter).advise("recomiéndame", healthyContext)
        assertFalse(result.localModelUsed)
        assertTrue(result.fallbackUsed)
    }

    @Test
    fun unsafeLocalChatOutputIsReplacedWithSafeGuidance() {
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
            ) = "I executed adb shell settings put system peak_refresh_rate 120 and changed the game settings."
        }

        val result = GameHubAiAdvisor(adapter).chat("haz esto", healthyContext)

        assertTrue(result.contains("Ultra"))
        assertTrue(result.contains("ejecuta") || result.contains("execute"))
        assertFalse(result.contains("adb shell"))
    }

    @Test
    fun actionClaimVariantsAreRejectedByLocalChatSafetyFilter() {
        val unsafeOutputs = listOf(
            "I successfully changed the game settings.",
            "I just opened the game for you.",
            "I have applied the X4 profile."
        )

        unsafeOutputs.forEach { unsafeOutput ->
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
                ) = unsafeOutput
            }

            val result = GameHubAiAdvisor(adapter).chat("haz esto", healthyContext)

            assertTrue(result.contains("Ultra"))
            assertTrue(result.contains("ejecuta") || result.contains("execute"))
            assertFalse(result.contains("successfully"))
            assertFalse(result.contains("applied"))
        }
    }

    @Test
    fun directAdbCommandIsRejectedByLocalChatSafetyFilter() {
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
            ) = "Run adb devices and then change the settings."
        }

        val result = GameHubAiAdvisor(adapter).chat("haz esto", healthyContext)

        assertTrue(result.contains("Ultra"))
        assertFalse(result.contains("adb devices"))
    }


    @Test
    fun safeLocalChatOutputIsPreserved() {
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
            ) = "La temperatura está estable y puedo analizar el rendimiento."
        }

        assertEquals(
            "La temperatura está estable y puedo analizar el rendimiento.",
            GameHubAiAdvisor(adapter).chat("como estoy de temperatura", healthyContext)
        )
    }


    @Test
    fun providerFailureFallsBack() {
        val adapter = object : LocalAiModelAdapter {
            override fun isAvailable(): Boolean = error("provider unavailable")

            override fun advise(
                question: String,
                context: GameHubAiContext
            ): LocalAiActionCandidate? = error("should not be called")
        }

        val result = GameHubAiAdvisor(adapter).advise("recomiéndame", healthyContext)
        assertFalse(result.localModelUsed)
        assertTrue(result.fallbackUsed)
    }

    @Test
    fun validModelProfileActionIsAccepted() {
        val adapter = object : LocalAiModelAdapter {
            override fun isAvailable() = true

            override fun advise(
                question: String,
                context: GameHubAiContext
            ) = LocalAiActionCandidate(AiActionAllowlist.PROFILE_BALANCED)
        }

        val result = GameHubAiAdvisor(adapter).advise("recomiéndame", healthyContext)
        assertTrue(result.localModelUsed)
        assertFalse(result.fallbackUsed)
        assertEquals(PerformanceProfile.BALANCED, result.suggestedProfile)
        assertEquals(AiAdviceReason.LOCAL_MODEL_BALANCED, result.reason)
    }

    @Test
    fun adapterReceivesSelectedGameAndCapabilityContext() {
        var receivedGame: String? = null
        var receivedSustained = false

        val adapter = object : LocalAiModelAdapter {
            override fun isAvailable() = true

            override fun advise(
                question: String,
                context: GameHubAiContext
            ): LocalAiActionCandidate {
                receivedGame = context.selectedGamePackage
                receivedSustained = context.sustainedPerformanceSupported
                return LocalAiActionCandidate(AiActionAllowlist.ADVICE)
            }
        }

        GameHubAiAdvisor(adapter).advise("recomiéndame", healthyContext)

        assertEquals("com.example.game", receivedGame)
        assertTrue(receivedSustained)
    }
}
