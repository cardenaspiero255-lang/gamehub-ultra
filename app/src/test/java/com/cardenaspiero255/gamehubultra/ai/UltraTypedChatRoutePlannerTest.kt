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
        route as UltraAgentRoute.Chat
        assertEquals("¿qué es Vulkan?", route.message)
        assertEquals(UltraVerificationMode.OPTIONAL, route.query.verificationMode)
        assertFalse(route.query.requiresInternet)
        assertFalse(route.query.requiresFreshData)
    }

    @Test
    fun typedCasualConversationStaysOnLocalRoute() {
        val route = UltraTypedChatRoutePlanner.route(
            message = "hola, cómo estás",
            conversationHistory = emptyList()
        )

        assertTrue(route is UltraAgentRoute.Chat)
        route as UltraAgentRoute.Chat
        assertEquals(UltraVerificationMode.LOCAL, route.query.verificationMode)
        assertFalse(route.query.requiresInternet)
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
