package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UltraTypedChatRoutePlannerTest {

    @Test
    fun typedStableFactualQuestionUsesOptionalKnowledgeRoute() {
        val route = UltraTypedChatRoutePlanner.route(
            message = "¿qué es Vulkan?",
            conversationHistory = listOf("Tú: hola", "Ultra: Hola.")
        )

        assertTrue(route is UltraAgentRoute.Chat)
        val query = requireNotNull(route.query)
        assertEquals("¿qué es Vulkan?", route.message)
        assertEquals(UltraVerificationMode.OPTIONAL, query.verificationMode)
        assertFalse(query.requiresInternet)
        assertFalse(query.requiresFreshData)
    }

    @Test
    fun typedCasualConversationStaysOnLocalRoute() {
        val route = UltraTypedChatRoutePlanner.route(
            message = "hola, cómo estás",
            conversationHistory = emptyList()
        )

        assertTrue(route is UltraAgentRoute.Chat)
        val query = requireNotNull(route.query)
        assertEquals(UltraVerificationMode.LOCAL, query.verificationMode)
        assertFalse(query.requiresInternet)
    }

    @Test
    fun typedProfileActionUsesCommandRoute() {
        val route = UltraTypedChatRoutePlanner.route(
            message = "pon X4",
            conversationHistory = emptyList()
        )

        assertTrue(route is UltraAgentRoute.Command)
    }
}
