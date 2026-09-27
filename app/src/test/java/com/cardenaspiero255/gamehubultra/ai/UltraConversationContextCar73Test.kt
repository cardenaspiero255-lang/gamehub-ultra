package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFalse

class UltraConversationContextCar73Test {

    @Test
    fun comparisonFollowUpReusesRecentEntities() {
        val history = listOf(
            "Tú: Ultra, compara RedMagic 11S Pro con Samsung Galaxy S26 Ultra",
            "Ultra: Estoy comparando ambos teléfonos."
        )

        val resolved = UltraConversationContextResolver.resolve(
            message = "¿Y cuál tiene mejor batería?",
            conversation = history
        )

        assertContains(resolved, "RedMagic 11S Pro")
        assertContains(resolved, "Samsung Galaxy S26 Ultra")
        assertContains(resolved, "cuál tiene mejor batería", ignoreCase = true)
    }

    @Test
    fun completeNewQuestionDoesNotDragOldComparisonContext() {
        val history = listOf(
            "Tú: Ultra, compara RedMagic 11S Pro con Samsung Galaxy S26 Ultra",
            "Ultra: Estoy comparando ambos teléfonos."
        )

        val resolved = UltraConversationContextResolver.resolve(
            message = "Ultra, qué es Vulkan",
            conversation = history
        )

        assertEquals("Ultra, qué es Vulkan", resolved)
        assertFalse(resolved.contains("RedMagic"))
    }

    @Test
    fun routedFollowUpCarriesResolvedContextIntoResearchMetadata() {
        val history = listOf(
            "Tú: Ultra, compara RedMagic 11S Pro con Samsung Galaxy S26 Ultra",
            "Ultra: El contexto de comparación quedó activo."
        )

        val route = UltraUnifiedAgentRouter.route(
            transcript = "Ultra, ¿y cuál tiene mejor refrigeración?",
            optionalResolver = null,
            conversationHistory = history
        )

        val chat = assertIs<UltraAgentRoute.Chat>(route)
        val query = requireNotNull(chat.query)
        assertEquals(UltraGeneralQueryKind.COMPARISON_RESEARCH, query.kind)
        assertContains(query.originalText, "RedMagic 11S Pro")
        assertContains(query.originalText, "Samsung Galaxy S26 Ultra")
        assertContains(query.originalText, "mejor refrigeración", ignoreCase = true)
    }

    @Test
    fun utilityFastPathStillWinsEvenWithComparisonHistory() {
        val history = listOf(
            "Tú: Ultra, compara RedMagic 11S Pro con Samsung Galaxy S26 Ultra"
        )

        val route = UltraUnifiedAgentRouter.route(
            transcript = "Ultra dime la hora",
            optionalResolver = null,
            conversationHistory = history
        )

        val utility = assertIs<UltraAgentRoute.Utility>(route)
        assertEquals(UltraUtilityIntent.CurrentTime, utility.answer.intent)
    }
}
