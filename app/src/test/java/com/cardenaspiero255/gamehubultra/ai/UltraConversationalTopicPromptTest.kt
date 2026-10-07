package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UltraConversationalTopicPromptTest {
    @Test
    fun conversationalTopicPhrasesRouteToGeneralKnowledgeChat() {
        val phrases = listOf(
            "Ultra, háblame de la marca Nike",
            "Ultra, háblame de los osos",
            "Ultra, cuéntame sobre los osos",
            "Ultra, dime sobre los osos",
            "Ultra, quiero saber sobre los osos",
            "Ultra, qué sabes de los osos",
            "Ultra, dame información sobre los osos",
            "Ultra, infórmame acerca de los osos",
            "Ultra, descríbeme los osos"
        )

        phrases.forEach { transcript ->
            val route = UltraUnifiedAgentRouter.route(transcript)
            val chat = assertIs<UltraAgentRoute.Chat>(route, transcript)
            assertEquals(
                UltraGeneralQueryKind.GENERAL_KNOWLEDGE,
                chat.query?.kind,
                transcript
            )
            assertEquals(
                UltraVerificationMode.OPTIONAL,
                chat.query?.verificationMode,
                transcript
            )
        }
    }

    @Test
    fun talkAboutBearsCanUseStableLocalKnowledgeWithoutInternet() {
        val advisor = GameHubAiAdvisor()

        val answer = advisor.generalKnowledgeChatOrNull(
            message = "Ultra, háblame de los osos",
            context = GameHubAiContext(),
            conversation = emptyList()
        )

        assertNotNull(answer)
        assertTrue(answer.contains("oso", ignoreCase = true), answer)
        assertTrue(answer.contains("Ursidae", ignoreCase = true), answer)
    }

    @Test
    fun freshConversationalTopicStillRequiresFreshVerification() {
        val request = UltraGeneralQueryRouter.classify(
            "Ultra, háblame de las noticias actuales de Nike"
        )

        assertEquals(UltraGeneralQueryKind.CURRENT_DATA, request.kind)
        assertEquals(UltraVerificationMode.REQUIRED, request.verificationMode)
        assertTrue(request.requiresFreshData)
    }
}
