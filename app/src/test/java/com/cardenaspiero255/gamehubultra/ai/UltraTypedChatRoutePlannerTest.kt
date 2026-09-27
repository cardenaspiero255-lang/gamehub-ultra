package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UltraTypedChatRoutePlannerTest {

    @Test
    fun typedFactualQuestionUsesVerifiedResearchRoute() {
        val route = UltraTypedChatRoutePlanner.route(
            message = "¿qué es Vulkan?",
            conversationHistory = listOf("Tú: hola", "Ultra: Hola.")
        )

        assertEquals("¿qué es Vulkan?", route.message)
        assertTrue(route.query?.requiresInternet == true)
        assertFalse(route.query?.requiresFreshData == true)
    }

    @Test
    fun typedCasualConversationStaysOnLocalRoute() {
        val route = UltraTypedChatRoutePlanner.route(
            message = "hola, cómo estás",
            conversationHistory = emptyList()
        )

        assertFalse(route.query?.requiresInternet == true)
    }
}
