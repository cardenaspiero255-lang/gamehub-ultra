package com.cardenaspiero255.gamehubultra.ai

import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.voice.VoiceCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
    fun freshKnowledgeRequestDoesNotUseDeterministicOfflineFallback() {
        val advisor = GameHubAiAdvisor(modelAdapter = null)

        assertNull(
            advisor.generalKnowledgeChatOrNull(
                message = "Ultra, ¿cuál es el precio actual de Bitcoin?",
                context = healthyContext
            )
        )
    }

    @Test
    fun tikTokDefinitionHasOfflineStableFallback() {
        val answer = GameHubAiAdvisor().generalKnowledgeChatOrNull(
            message = "Ultra, ¿Qué es tik Tok?",
            context = healthyContext,
            conversation = emptyList()
        )

        assertNotNull(answer)
        assertTrue(answer.contains("TikTok"))
        assertTrue(answer.contains("videos", ignoreCase = true))
    }

    @Test
    fun generalKnowledgeFallbackReturnsSubstantiveLocalModelAnswer() {
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
            ): String = "Vulkan es una API gráfica multiplataforma."
        }

        val answer = GameHubAiAdvisor(adapter).generalKnowledgeChatOrNull(
            message = "Ultra, ¿qué es Vulkan?",
            context = healthyContext
        )

        assertEquals("Vulkan es una API gráfica multiplataforma.", answer)
    }

    @Test
    fun stableGeneralQuestionDoesNotCollapseToCapabilityBoilerplateOffline() {
        val answer = GameHubAiAdvisor().chat(
            message = "Ultra, ¿qué son los sentimientos?",
            context = healthyContext
        )

        assertTrue(answer.contains("sentimientos", ignoreCase = true))
        assertFalse(answer.startsWith("Soy Ultra. Puedo ayudarte"))
    }

    @Test
    fun emotionVariantCoversStableOfflineKnowledgeBranch() {
        val answer = GameHubAiAdvisor().generalKnowledgeChatOrNull(
            message = "Ultra, ¿qué es una emoción?",
            context = healthyContext,
            conversation = emptyList()
        )

        assertNotNull(answer)
        assertTrue(answer.contains("emociones", ignoreCase = true))
        assertTrue(answer.contains("respuestas psicofisiológicas", ignoreCase = true))
    }

    @Test
    fun unrelatedEmotionStatementDoesNotTriggerDefinitionFallback() {
        val answer = GameHubAiAdvisor().generalKnowledgeChatOrNull(
            message = "Ese final fue muy emocionante y me dejó con emociones mezcladas",
            context = healthyContext,
            conversation = emptyList()
        )

        assertNull(answer)
    }

    @Test
    fun definitionCueInsideStatementDoesNotTriggerStableKnowledgeFallback() {
        val answer = GameHubAiAdvisor().generalKnowledgeChatOrNull(
            message = "Ese final es lo que define mis emociones",
            context = healthyContext,
            conversation = emptyList()
        )

        assertNull(answer)
    }

    @Test
    fun consentQuestionDoesNotMatchSentimentFallbackBySubstring() {
        val answer = GameHubAiAdvisor().generalKnowledgeChatOrNull(
            message = "¿Qué es el consentimiento informado?",
            context = healthyContext,
            conversation = emptyList()
        )

        assertNull(answer)
    }

    @Test
    fun photosynthesisHasOfflineStableFallbackWithoutNetworkOrLocalModel() {
        val answer = GameHubAiAdvisor().generalKnowledgeChatOrNull(
            message = "¿Qué es la fotosíntesis?",
            context = healthyContext,
            conversation = emptyList()
        )

        assertNotNull(answer)
        assertTrue(answer.contains("fotosíntesis", ignoreCase = true))
        assertTrue(answer.contains("luz", ignoreCase = true))
    }

    @Test
    fun commonStableKnowledgeCorpusHasOfflineFallbacks() {
        val cases = listOf(
            "¿Qué es un motor?" to "energía",
            "¿Qué es el ADN?" to "genética",
            "¿Qué es una célula?" to "vida",
            "¿Qué es un agujero negro?" to "gravedad",
            "¿Qué es un algoritmo?" to "pasos",
            "¿Qué es una API?" to "software",
            "¿Qué es la memoria RAM?" to "memoria",
            "¿Qué es una GPU?" to "gráficos",
            "¿Qué es inteligencia artificial?" to "tareas"
        )

        cases.forEach { (question, expectedKeyword) ->
            val answer = GameHubAiAdvisor().generalKnowledgeChatOrNull(
                message = question,
                context = healthyContext,
                conversation = emptyList()
            )
            assertNotNull(answer, question)
            assertTrue(
                answer.contains(expectedKeyword, ignoreCase = true),
                "$question -> $answer"
            )
        }
    }

    @Test
    fun unknownStableQuestionStillRefusesToInventOfflineKnowledge() {
        val answer = GameHubAiAdvisor().generalKnowledgeChatOrNull(
            message = "¿Qué es el frobnizador cuántico doméstico?",
            context = healthyContext,
            conversation = emptyList()
        )

        assertEquals(null, answer)
    }

    @Test
    fun deterministicChatAlwaysRespondsInSpanishEvenForEnglishInput() {
        val answer = GameHubAiAdvisor().chat(
            message = "Ultra, what is my battery?",
            context = healthyContext
        )

        assertTrue(answer.contains("batería", ignoreCase = true))
        assertFalse(answer.contains("The current battery", ignoreCase = true))
    }

    @Test
    fun englishLocalModelRepliesAreRejectedInFavorOfSpanishFallback() {
        listOf(
            "I'm Ultra and I can help you with your battery and performance.",
            "Hello, how are you?",
            "I'm Ultra.",
            "Vulkan is a low-level graphics API.",
            "Sure, I can explain that."
        ).forEach { modelReply ->
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
                ): String = modelReply
            }

            val answer = GameHubAiAdvisor(adapter).chat(
                message = "Ultra, hello",
                context = healthyContext
            )

            assertTrue(answer.contains("Soy Ultra"))
            assertFalse(answer.contains(modelReply))
        }
    }

    @Test
    fun SpanishReplyWithEnglishProperNameIsKept() {
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
            ): String = "I Am Alive es un videojuego de acción."
        }

        val answer = GameHubAiAdvisor(adapter).chat(
            message = "Ultra, háblame de I Am Alive",
            context = healthyContext
        )

        assertEquals("I Am Alive es un videojuego de acción.", answer)
    }

    @Test
    fun shortSpanishRepliesWithEnglishProperNamesAreKept() {
        listOf(
            "I Am Alive es divertido.",
            "Hello Games es un estudio de videojuegos."
        ).forEach { modelReply ->
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
                ): String = modelReply
            }

            val answer = GameHubAiAdvisor(adapter).chat(
                message = "Ultra, háblame de ese juego",
                context = healthyContext
            )

            assertEquals(modelReply, answer)
        }
    }

    @Test
    fun geminiChatPromptForcesSpanishOutput() {
        val prompt = buildGeminiChatPrompt(
            message = "Hello, how are you?",
            context = healthyContext,
            conversation = emptyList()
        )

        assertTrue(prompt.contains("Responde siempre en español"))
        assertFalse(prompt.contains("Answer in the same language as the user"))
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
    fun memoryRecallTakesPriorityOverStableKnowledgeFallback() {
        val gateway = fixedMemoryGateway("mis emociones cambian mucho cuando juego competitivo")

        val answer = GameHubAiAdvisor(
            modelAdapter = null,
            memoryGateway = gateway
        ).chat(
            message = "Ultra, ¿qué recuerdas de mis emociones?",
            context = healthyContext
        )

        assertTrue(answer.contains("mis emociones cambian mucho"))
        assertFalse(answer.startsWith("Los sentimientos son"))
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


    @Test
    fun reportedStableDefinitionsHaveOfflineFallbacks() {
        val cases = listOf(
            "¿Qué es un avión?" to "aeronave",
            "¿Qué es un psicópata?" to "rasgos",
            "¿Qué es un lápiz?" to "escribir"
        )

        cases.forEach { (question, expectedKeyword) ->
            val answer = GameHubAiAdvisor().generalKnowledgeChatOrNull(
                message = question,
                context = healthyContext,
                conversation = emptyList()
            )

            assertNotNull(answer, question)
            assertTrue(
                answer.contains(expectedKeyword, ignoreCase = true),
                "$question -> $answer"
            )
        }
    }

    @Test
    fun deterministicAdviceUsesUltraAiCoreProductionPath() {
        var receivedObservation: UltraAiObservation? = null
        val forcingCore = object : UltraAiCoreGateway {
            override fun evaluate(
                observation: UltraAiObservation,
                feedback: UltraAiFeedbackSnapshot,
                memories: List<UltraAiMemorySignal>
            ): UltraAiCoreResult {
                receivedObservation = observation
                return UltraAiCoreResult(
                    recommendation = UltraAiRecommendation(
                        profileId = "BALANCED",
                        confidence = 1.0,
                        evidence = listOf("test-core"),
                        source = UltraAiRecommendationSource.DETERMINISTIC_LOCAL
                    ),
                    explanation = "test",
                    memorySignals = memories,
                    requiresCloud = false
                )
            }
        }

        val result = GameHubAiAdvisor(
            modelAdapter = null,
            memoryGateway = null,
            aiCore = forcingCore
        ).advise("qué modo me recomiendas", healthyContext)

        assertNotNull(receivedObservation)
        assertEquals("com.example.game", receivedObservation.gamePackage)
        assertEquals(PerformanceProfile.BALANCED, result.suggestedProfile)
        assertFalse(result.localModelUsed)
        assertTrue(result.fallbackUsed)
    }


    @Test
    fun coreOverridesMapProfilesToExpectedReasons() {
        fun advisorReturning(profileId: String): GameHubAiAdvisor =
            GameHubAiAdvisor(
                aiCore = object : UltraAiCoreGateway {
                    override fun evaluate(
                        observation: UltraAiObservation,
                        feedback: UltraAiFeedbackSnapshot,
                        memories: List<UltraAiMemorySignal>
                    ): UltraAiCoreResult =
                        UltraAiCoreResult(
                            recommendation = UltraAiRecommendation(
                                profileId = profileId,
                                confidence = 0.95,
                                evidence = listOf("test")
                            ),
                            explanation = "test",
                            memorySignals = memories,
                            requiresCloud = false
                        )
                }
            )

        val constrainedContext = healthyContext.copy(
            thermalStatus = 3,
            thermalHeadroom = 0.90f
        )

        val x4 = advisorReturning("X4").advise(
            "qué modo me recomiendas",
            constrainedContext
        )
        assertEquals(PerformanceProfile.X4, x4.suggestedProfile)
        assertEquals(AiAdviceReason.X4_READY, x4.reason)

        val interpolation = advisorReturning("FRAME_INTERPOLATION").advise(
            "qué modo me recomiendas",
            constrainedContext
        )
        assertEquals(
            PerformanceProfile.FRAME_INTERPOLATION,
            interpolation.suggestedProfile
        )
        assertEquals(AiAdviceReason.INTERPOLATION, interpolation.reason)
    }

    @Test
    fun coreFallbackKeepsBaseAdviceWhenCloudInvalidOrExceptionOccurs() {
        fun advisorWith(
            profileId: String = "BALANCED",
            requiresCloud: Boolean = false,
            fail: Boolean = false
        ): GameHubAiAdvisor =
            GameHubAiAdvisor(
                aiCore = object : UltraAiCoreGateway {
                    override fun evaluate(
                        observation: UltraAiObservation,
                        feedback: UltraAiFeedbackSnapshot,
                        memories: List<UltraAiMemorySignal>
                    ): UltraAiCoreResult {
                        if (fail) error("synthetic core failure")
                        return UltraAiCoreResult(
                            recommendation = UltraAiRecommendation(
                                profileId = profileId,
                                confidence = 0.5,
                                evidence = emptyList()
                            ),
                            explanation = "test",
                            memorySignals = memories,
                            requiresCloud = requiresCloud
                        )
                    }
                }
            )

        val baseline = GameHubAiAdvisor().advise(
            "qué modo me recomiendas",
            healthyContext
        )
        assertEquals(PerformanceProfile.X4, baseline.suggestedProfile)

        val cloud = advisorWith(requiresCloud = true).advise(
            "qué modo me recomiendas",
            healthyContext
        )
        assertEquals(baseline.suggestedProfile, cloud.suggestedProfile)
        assertEquals(baseline.reason, cloud.reason)

        val invalid = advisorWith(profileId = "NOT_A_PROFILE").advise(
            "qué modo me recomiendas",
            healthyContext
        )
        assertEquals(baseline.suggestedProfile, invalid.suggestedProfile)
        assertEquals(baseline.reason, invalid.reason)

        val failed = advisorWith(fail = true).advise(
            "qué modo me recomiendas",
            healthyContext
        )
        assertEquals(baseline.suggestedProfile, failed.suggestedProfile)
        assertEquals(baseline.reason, failed.reason)
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
