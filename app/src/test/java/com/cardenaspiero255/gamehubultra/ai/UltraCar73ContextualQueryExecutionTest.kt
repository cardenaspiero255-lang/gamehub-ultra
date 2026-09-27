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
    fun stableGeneralKnowledgeStaysOnLocalFastPath() {
        val engine = UltraVerifiedResearchEngine(emptyList())
        val coordinator = UltraQueryExecutionCoordinator(engine)
        var localCalls = 0

        val answer = coordinator.answer(
            request = UltraGeneralQueryRouter.classify("Ultra, explícame qué es Vulkan"),
            localChat = {
                localCalls += 1
                "Vulkan es una API gráfica."
            }
        )

        assertEquals("Vulkan es una API gráfica.", answer.message)
        assertFalse(answer.verified)
        assertEquals(1, localCalls)
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


    @Test
    fun stableGeneralKnowledgeUsesVerifiedResearchWhenLocalModelCannotAnswer() {
        val provider = object : UltraResearchProvider {
            override val id = "trusted-reference"
            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence =
                UltraResearchEvidence(
                    claimKey = "general:sky-blue",
                    value = "rayleigh-scattering",
                    displayText = "El cielo se ve azul principalmente por la dispersión de Rayleigh.",
                    sourceId = "https://es.wikipedia.org/wiki/Dispersi%C3%B3n_de_Rayleigh",
                    trustedReference = true
                )
        }
        val engine = UltraVerifiedResearchEngine(listOf(provider))
        val coordinator = UltraQueryExecutionCoordinator(engine)
        var localCalls = 0

        val answer = coordinator.answer(
            request = UltraGeneralQueryRouter.classify("Ultra, ¿por qué el cielo es azul?"),
            localChat = {
                localCalls += 1
                "Soy Ultra. Puedo hablar contigo sobre rendimiento, FPS, temperatura, batería, red y perfiles de GameHub Ultra. En este dispositivo el chat local puede estar limitado si no hay un modelo compatible."
            }
        )

        assertEquals(
            "El cielo se ve azul principalmente por la dispersión de Rayleigh.",
            answer.message
        )
        assertTrue(answer.verified)
        assertEquals(1, localCalls)
        engine.close()
    }

}
