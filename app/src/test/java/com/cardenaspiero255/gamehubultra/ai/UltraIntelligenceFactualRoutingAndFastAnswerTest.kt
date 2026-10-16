package com.cardenaspiero255.gamehubultra.ai

import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Behavioral benchmark for EVO V3 intelligence: precise factual routing,
 * fresh-data boundaries and a fast offline response for curated knowledge.
 */
class UltraIntelligenceFactualRoutingAndFastAnswerTest {
    private val context = GameHubAiContext(
        selectedGamePackage = null,
        sustainedPerformanceSupported = false,
        cpuCores = 8,
        totalRamMb = 8192,
        gpuAvailable = true,
        thermalStatus = 0,
        thermalHeadroom = null,
        batteryPercent = 75,
        charging = false,
        refreshRateHz = 120f,
        networkValidated = true,
        networkLatencyMs = 40L,
        downstreamBandwidthKbps = 30_000L,
        storageFreePercent = 50,
        inputDeviceCount = 0,
        selectedProfile = PerformanceProfile.BALANCED,
        sessionActive = false
    )

    @Test
    fun unseenFactualQuestionShapesUseOptionalEvidenceInsteadOfGenericLocalChat() {
        val questions = listOf(
            "Ultra, ¿Quién descubrió la penicilina?",
            "¿Quién escribió Don Quijote?",
            "¿Cuánto mide el monte Everest?",
            "¿Dónde nació Nikola Tesla?",
            "¿Cuándo se inventó el telescopio?",
            "¿Cómo surgió el universo?",
            "Ultra, ¿en qué consiste la teoría de la evolución?",
            "¿Qué causó la extinción de los dinosaurios?",
            "Who invented the telephone?",
            "Where was Ada Lovelace born?",
            "How was penicillin discovered?",
            "What caused the extinction of dinosaurs?"
        )
        questions.forEach { question ->
            val request = UltraGeneralQueryRouter.classify(question)
            assertEquals(UltraGeneralQueryKind.GENERAL_KNOWLEDGE, request.kind, question)
            assertEquals(UltraVerificationMode.OPTIONAL, request.verificationMode, question)
            assertFalse(request.requiresFreshData, question)
            assertFalse(request.requiresInternet, question)
            assertIs<UltraAgentRoute.Chat>(UltraUnifiedAgentRouter.route(question), question)
        }
    }

    @Test
    fun unknownFactualQuestionsUseAWorkingResearchProviderInsteadOfGreetingBoilerplate() {
        var researchCalls = 0
        val coordinator = UltraQueryExecutionCoordinator(object : UltraResearchGateway {
            override fun answer(request: UltraGeneralQueryRequest): UltraVerifiedResearchResult {
                researchCalls++
                return UltraVerifiedResearchResult(
                    message = "Respuesta contrastada para: " + request.originalText,
                    confidence = UltraAnswerConfidence.HIGH,
                    sources = listOf("https://example.org/fact"),
                    independentSourceCount = 1,
                    abstained = false
                )
            }
        })
        val question = "Ultra, ¿quién descubrió la penicilina?"
        val route = assertIs<UltraAgentRoute.Chat>(UltraUnifiedAgentRouter.route(question))
        var boilerplateCalls = 0
        val result = DefaultUltraQueryExecutor(coordinator).answer(
            route = route,
            stableKnowledgeFallback = { null },
            localChat = {
                boilerplateCalls++
                "Soy Ultra. Puedo ayudarte con preguntas generales."
            }
        )
        assertTrue(result.startsWith("Respuesta contrastada"), result)
        assertEquals(1, researchCalls)
        assertEquals(0, boilerplateCalls)
    }

    @Test
    fun pricesAndExchangeRatesNeverDowngradeToOfflineFact() {
        listOf(
            "Ultra, ¿cuánto vale Bitcoin ahora?",
            "¿Cuánto vale un RedMagic 12 Pro?",
            "¿Cuál es la cotización del dólar?",
            "What is the USD to CLP exchange rate today?",
            "¿A cuánto está el dólar?"
        ).forEach { question ->
            val request = UltraGeneralQueryRouter.classify(question)
            assertEquals(UltraVerificationMode.REQUIRED, request.verificationMode, question)
            assertTrue(request.requiresFreshData, question)
            assertTrue(request.requiresInternet, question)
        }
    }

    @Test
    fun stableDefinitionsRemainStableWhileCasualTalkAndCommandsRemainLocal() {
        val stable = listOf(
            "Ultra, ¿qué es el tipo de cambio?",
            "¿Qué significa cotización?",
            "¿Qué es el precio de equilibrio?"
        )
        stable.forEach {
            val query = UltraGeneralQueryRouter.classify(it)
            assertEquals(UltraVerificationMode.OPTIONAL, query.verificationMode, it)
            assertFalse(query.requiresFreshData, it)
        }
        listOf("Hola, Ultra", "Gracias Ultra", "Ultra, ¿cómo estás?").forEach {
            assertEquals(UltraVerificationMode.LOCAL,
                UltraGeneralQueryRouter.classify(it).verificationMode, it)
        }
        assertIs<UltraAgentRoute.Command>(
            UltraUnifiedAgentRouter.route("Ultra, abre Minecraft")
        )
        assertIs<UltraAgentRoute.Chat>(
            UltraUnifiedAgentRouter.route("Ultra, no desactives X4")
        )
    }

    @Test
    fun shortCuratedDefinitionsSkipTheSlowerLocalModel() {
        var localModelCalls = 0
        val model = object : LocalAiModelAdapter {
            override fun isAvailable(): Boolean = true
            override fun advise(
                question: String,
                context: GameHubAiContext
            ): LocalAiActionCandidate? = null
            override fun chat(
                message: String,
                context: GameHubAiContext,
                conversation: List<String>
            ): String? {
                localModelCalls++
                return "Respuesta detallada del modelo local."
            }
        }
        val advisor = GameHubAiAdvisor(modelAdapter = model, preferFastStableDefinitions = true)
        val short = advisor.generalKnowledgeChatOrNull(
            "Ultra, ¿qué es el Big Bang?", context, emptyList()
        ).orEmpty()
        assertTrue(short.contains("universo", ignoreCase = true), short)
        assertEquals(0, localModelCalls, "Curated stable knowledge should bypass model latency")

        val detailed = advisor.generalKnowledgeChatOrNull(
            "Ultra, explícame detalladamente el Big Bang", context, emptyList()
        )
        assertEquals("Respuesta detallada del modelo local.", detailed)
        assertEquals(1, localModelCalls)
    }

    @Test
    fun freshQuestionNeverUsesCuratedKnowledgeAsAVerifiedPrice() {
        var researchCalls = 0
        val executor = DefaultUltraQueryExecutor(
            coordinator = UltraQueryExecutionCoordinator(object : UltraResearchGateway {
                override fun answer(request: UltraGeneralQueryRequest): UltraVerifiedResearchResult {
                    researchCalls++
                    return UltraVerifiedResearchResult(
                        message = "No hay precio confirmado con fuentes.",
                        confidence = UltraAnswerConfidence.LOW,
                        sources = emptyList(),
                        abstained = true,
                        retryable = false
                    )
                }
            })
        )
        val route = assertIs<UltraAgentRoute.Chat>(
            UltraUnifiedAgentRouter.route("Ultra, ¿cuánto vale Bitcoin ahora?")
        )
        val result = executor.answer(
            route = route,
            stableKnowledgeFallback = { error("Fresh data cannot use local corpus") },
            localChat = { error("Fresh data cannot use stale offline response") }
        )
        assertEquals(1, researchCalls)
        assertFalse(result.contains("Respuesta local"))
    }
}
