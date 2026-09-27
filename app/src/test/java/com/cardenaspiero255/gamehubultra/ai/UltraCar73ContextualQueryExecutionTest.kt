package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UltraCar73ContextualQueryExecutionTest {

    @Test
    fun comparisonFollowUpKeepsComparisonAndAddsPreviousEntities() {
        val plan = UltraContextualQueryPlanner.plan(
            message = "¿y cuál tiene mejor batería?",
            conversationHistory = listOf(
                "Tú: Ultra, compara el RedMagic 11S Pro con el Galaxy S26 Ultra",
                "Ultra: Estoy comparándolos."
            )
        )

        assertEquals(UltraGeneralQueryKind.COMPARISON_RESEARCH, plan.kind)
        assertTrue(plan.originalText.contains("RedMagic 11S Pro"))
        assertTrue(plan.originalText.contains("Galaxy S26 Ultra"))
        assertTrue(plan.originalText.contains("mejor batería"))
    }

    @Test
    fun explicitFreshQuestionIsNotContaminatedByOlderComparison() {
        val plan = UltraContextualQueryPlanner.plan(
            message = "¿y cuánto cuesta hoy el RedMagic 11S Pro?",
            conversationHistory = listOf(
                "Tú: Ultra, compara el RedMagic 11S Pro con el Galaxy S26 Ultra"
            )
        )

        assertEquals(UltraGeneralQueryKind.CURRENT_DATA, plan.kind)
        assertTrue(plan.requiresFreshData)
        assertEquals(20_000L, plan.timeoutMillis)
    }

    @Test
    fun explicitNewTopicDoesNotReuseOldComparisonContext() {
        val plan = UltraContextualQueryPlanner.plan(
            message = "Ultra, explícame qué es Vulkan",
            conversationHistory = listOf(
                "Tú: Ultra, compara el RedMagic 11S Pro con el Galaxy S26 Ultra"
            )
        )

        assertEquals(UltraGeneralQueryKind.GENERAL_KNOWLEDGE, plan.kind)
        assertFalse(plan.originalText.contains("RedMagic 11S Pro"))
    }


    @Test
    fun priceQuestionBeginningWithDefinitionPhraseStillRequiresFreshData() {
        val request = UltraGeneralQueryRouter.classify("what is the price of Bitcoin")

        assertEquals(UltraGeneralQueryKind.CURRENT_DATA, request.kind)
        assertTrue(request.requiresInternet)
        assertTrue(request.requiresFreshData)
    }

    @Test
    fun onlineQueryUsesVerifiedResearchInsteadOfLocalChat() {
        val provider = object : UltraResearchProvider {
            override val id = "official-weather"
            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence =
                UltraResearchEvidence(
                    claimKey = "weather",
                    value = "22-sunny",
                    displayText = "22 °C y despejado",
                    sourceId = "official-weather",
                    authoritative = true
                )
        }
        val engine = UltraVerifiedResearchEngine(listOf(provider))
        val coordinator = UltraQueryExecutionCoordinator(engine)
        var localCalls = 0

        val answer = coordinator.answer(
            request = UltraGeneralQueryRouter.classify("Ultra, clima de hoy"),
            localChat = {
                localCalls += 1
                "respuesta local"
            }
        )

        assertEquals("22 °C y despejado", answer.message)
        assertTrue(answer.verified)
        assertEquals(0, localCalls)
        engine.close()
    }

    @Test
    fun stableGeneralKnowledgeUsesVerifiedResearchInsteadOfGamingFallback() {
        val provider = object : UltraResearchProvider {
            override val id = "encyclopedia"
            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence =
                UltraResearchEvidence(
                    claimKey = "vulkan",
                    value = "graphics-api",
                    displayText = "Vulkan es una API gráfica multiplataforma.",
                    sourceId = "encyclopedia",
                    authoritative = true
                )
        }
        val engine = UltraVerifiedResearchEngine(listOf(provider))
        val coordinator = UltraQueryExecutionCoordinator(engine)
        var localCalls = 0

        val answer = coordinator.answer(
            request = UltraGeneralQueryRouter.classify("Ultra, explícame qué es Vulkan"),
            localChat = {
                localCalls += 1
                "fallback gaming"
            }
        )

        assertEquals("Vulkan es una API gráfica multiplataforma.", answer.message)
        assertTrue(answer.verified)
        assertEquals(0, localCalls)
        engine.close()
    }

    @Test
    fun generalKnowledgeFollowUpCarriesPreviousTopicIntoResearchPlan() {
        val plan = UltraContextualQueryPlanner.plan(
            message = "¿y para qué sirve?",
            conversationHistory = listOf(
                "Tú: Ultra, explícame qué es Vulkan",
                "Ultra: Vulkan es una API gráfica."
            )
        )

        assertEquals(UltraGeneralQueryKind.GENERAL_KNOWLEDGE, plan.kind)
        assertTrue(plan.requiresInternet)
        assertTrue(plan.originalText.contains("Vulkan"))
        assertTrue(plan.originalText.contains("para qué sirve"))
    }

    @Test
    fun stableGeneralKnowledgeFallsBackToLocalChatWhenResearchIsUnavailable() {
        val failingProvider = object : UltraResearchProvider {
            override val id = "offline-provider"

            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence {
                error("provider unavailable")
            }
        }
        val engine = UltraVerifiedResearchEngine(listOf(failingProvider))
        val coordinator = UltraQueryExecutionCoordinator(engine)
        var localCalls = 0

        val answer = coordinator.answer(
            request = UltraGeneralQueryRouter.classify(
                "Ultra, qué son los sentimientos"
            ),
            localChat = {
                localCalls += 1
                "Los sentimientos son experiencias afectivas que interpretamos a partir de emociones, pensamientos y contexto."
            }
        )

        assertEquals(
            "Los sentimientos son experiencias afectivas que interpretamos a partir de emociones, pensamientos y contexto.",
            answer.message
        )
        assertFalse(answer.verified)
        assertFalse(answer.abstained)
        assertTrue(answer.fallbackUsed)
        assertEquals(1, localCalls)
        engine.close()
    }

    @Test
    fun contradictoryEvidenceDoesNotFallBackToUnverifiedLocalChat() {
        fun provider(id: String, value: String) = object : UltraResearchProvider {
            override val id = id

            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence =
                UltraResearchEvidence(
                    claimKey = "sentimientos",
                    value = value,
                    displayText = "Definición de $id",
                    sourceId = id,
                    authoritative = false
                )
        }

        val request = UltraGeneralQueryRequest(
            originalText = "Ultra, qué son los sentimientos",
            kind = UltraGeneralQueryKind.GENERAL_KNOWLEDGE,
            requiresInternet = true,
            requiresFreshData = false,
            timeoutMillis = 5_000L
        )
        val engine = UltraVerifiedResearchEngine(
            listOf(
                provider("source-one", "definition-a"),
                provider("source-two", "definition-b")
            )
        )

        val directResearch = engine.answer(request)
        assertTrue(directResearch.abstained)
        assertEquals(
            setOf("source-one", "source-two"),
            directResearch.sources.toSet()
        )

        val coordinator = UltraQueryExecutionCoordinator(engine)
        var localCalls = 0
        val answer = coordinator.answer(
            request = request,
            localChat = {
                localCalls += 1
                "respuesta local no verificada"
            }
        )

        assertTrue(answer.abstained)
        assertFalse(answer.verified)
        assertEquals(
            setOf("source-one", "source-two"),
            answer.sources.toSet()
        )
        assertEquals(0, localCalls)
        engine.close()
    }

    @Test
    fun productionFreshQueryNeverFallsBackToUnverifiedLocalChat() {
        var localCalls = 0
        val route = UltraAgentRoute.Chat(
            message = "Ultra, clima de hoy",
            query = UltraGeneralQueryRouter.classify("Ultra, clima de hoy")
        )

        val answer = UltraProductionQueryExecutor.answer(route) {
            localCalls += 1
            "clima posiblemente desactualizado"
        }

        assertTrue(answer.contains("no pude verificar", ignoreCase = true))
        assertEquals(0, localCalls)
    }

}
