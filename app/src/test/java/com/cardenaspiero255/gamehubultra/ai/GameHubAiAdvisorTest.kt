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

    @Test
    fun memoryCommandIsHandledBeforeModelChat() {
        var modelChatInvoked = false
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
            ): String? {
                modelChatInvoked = true
                return "model"
            }
        }
        val gateway = object : UltraLongTermMemoryGateway {
            override fun handleCommand(message: String, scope: UltraMemoryScope): String? =
                if (message.contains("recuerda", ignoreCase = true)) {
                    "Lo recordaré: prefiero X4."
                } else {
                    null
                }

            override fun recallContext(
                message: String,
                scope: UltraMemoryScope,
                limit: Int
            ): List<UltraMemoryRecall> = emptyList()
        }

        val answer = GameHubAiAdvisor(
            modelAdapter = adapter,
            memoryGateway = gateway
        ).chat(
            message = "Ultra, recuerda que prefiero X4",
            context = healthyContext
        )

        assertEquals("Lo recordaré: prefiero X4.", answer)
        assertFalse(modelChatInvoked)
    }

    @Test
    fun relevantLongTermMemoryIsAddedToLocalModelContext() {
        var receivedConversation = emptyList<String>()
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
            ): String? {
                receivedConversation = conversation
                return "Usaré tu preferencia."
            }
        }
        val gateway = fixedMemoryGateway("prefiero el perfil X4 en este juego")

        val answer = GameHubAiAdvisor(
            modelAdapter = adapter,
            memoryGateway = gateway
        ).chat(
            message = "qué perfil prefiero",
            context = healthyContext,
            conversation = listOf("Tú: hola", "Ultra: hola")
        )

        assertEquals("Usaré tu preferencia.", answer)
        assertTrue(
            receivedConversation.any {
                it.contains("prefiero el perfil X4 en este juego")
            }
        )
    }

    @Test
    fun deterministicFallbackCanExplainRecalledMemoryOffline() {
        val gateway = fixedMemoryGateway("prefiero respuestas cortas")

        val answer = GameHubAiAdvisor(
            modelAdapter = null,
            memoryGateway = gateway
        ).chat(
            message = "Ultra, ¿qué recuerdas de mí?",
            context = healthyContext
        )

        assertTrue(answer.contains("prefiero respuestas cortas"))
    }


    @Test
    fun visibleConversationIsNotDuplicatedFromLongTermRecall() {
        var receivedConversation = emptyList<String>()
        val adapter = object : LocalAiModelAdapter {
            override fun isAvailable() = true
            override fun advise(question: String, context: GameHubAiContext): LocalAiActionCandidate? = null
            override fun chat(
                message: String,
                context: GameHubAiContext,
                conversation: List<String>
            ): String? {
                receivedConversation = conversation
                return "ok"
            }
        }
        val gateway = object : UltraLongTermMemoryGateway {
            override fun handleCommand(message: String, scope: UltraMemoryScope): String? = null
            override fun recallContext(
                message: String,
                scope: UltraMemoryScope,
                limit: Int
            ): List<UltraMemoryRecall> = listOf(
                UltraMemoryRecall(
                    record = UltraStoredMemory(
                        id = "duplicate",
                        kind = UltraMemoryKind.CONVERSATION,
                        role = UltraMemoryRole.USER,
                        text = "qué recuerdas de mí",
                        timestampMillis = 1L,
                        scope = scope
                    ),
                    score = 1.0,
                    provenance = UltraMemoryProvenance.PRIOR_CONVERSATION
                ),
                UltraMemoryRecall(
                    record = UltraStoredMemory(
                        id = "fact",
                        kind = UltraMemoryKind.FACT,
                        role = UltraMemoryRole.SYSTEM,
                        text = "prefiero X4",
                        timestampMillis = 2L,
                        scope = scope
                    ),
                    score = 0.9,
                    provenance = UltraMemoryProvenance.REMEMBERED_FACT
                )
            )
        }

        GameHubAiAdvisor(adapter, gateway).chat(
            message = "qué recuerdas de mí",
            context = healthyContext,
            conversation = listOf("Tú: qué recuerdas de mí")
        )

        assertFalse(receivedConversation.any { it.contains("Memoria previa") && it.contains("qué recuerdas de mí") })
        assertTrue(receivedConversation.any { it.contains("prefiero X4") })
    }

    private fun fixedMemoryGateway(text: String): UltraLongTermMemoryGateway =
        object : UltraLongTermMemoryGateway {
            override fun handleCommand(message: String, scope: UltraMemoryScope): String? = null

            override fun recallContext(
                message: String,
                scope: UltraMemoryScope,
                limit: Int
            ): List<UltraMemoryRecall> = listOf(
                UltraMemoryRecall(
                    record = UltraStoredMemory(
                        id = "memory-1",
                        kind = UltraMemoryKind.FACT,
                        role = UltraMemoryRole.SYSTEM,
                        text = text,
                        timestampMillis = 1L,
                        scope = scope
                    ),
                    score = 1.0,
                    provenance = UltraMemoryProvenance.REMEMBERED_FACT
                )
            )
        }
}
