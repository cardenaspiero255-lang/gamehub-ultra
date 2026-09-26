package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UltraCar73ResearchCoordinatorTest {
    @Test
    fun currentDataChatUsesVerifiedResearchBeforeLocalChatFallback() {
        val provider = fixedProvider(
            id = "weather-official",
            claimKey = "weather",
            value = "22-sunny",
            text = "Ahora hay 22 °C y está soleado.",
            authoritative = true
        )
        UltraGeneralResearchCoordinator(
            engine = UltraVerifiedResearchEngine(listOf(provider))
        ).use { coordinator ->
            val route = UltraAgentRoute.Chat(
                message = "Ultra, qué clima hay ahora",
                query = UltraGeneralQueryRouter.classify("Ultra, qué clima hay ahora")
            )

            val result = coordinator.answer(route)

            assertTrue(result.handled)
            assertFalse(result.result!!.abstained)
            assertTrue(result.result.message.contains("22"))
        }
    }

    @Test
    fun stableGeneralKnowledgeWithoutInternetFallsBackToLocalChat() {
        UltraGeneralResearchCoordinator(
            engine = UltraVerifiedResearchEngine(emptyList())
        ).use { coordinator ->
            val route = UltraAgentRoute.Chat(
                message = "Ultra, explícame qué es Vulkan",
                query = UltraGeneralQueryRouter.classify("Ultra, explícame qué es Vulkan")
            )

            val result = coordinator.answer(route)

            assertFalse(result.handled)
            assertEquals(null, result.result)
        }
    }

    @Test
    fun sensitiveCurrentQueryIsHandledBySafeAbstention() {
        var calls = 0
        val provider = object : UltraResearchProvider {
            override val id = "online"
            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence {
                calls++
                return UltraResearchEvidence("x", "x", "x", id)
            }
        }
        UltraGeneralResearchCoordinator(
            engine = UltraVerifiedResearchEngine(listOf(provider))
        ).use { coordinator ->
            val text = "Ultra, busca el precio actual con token=secret-value-123"
            val route = UltraAgentRoute.Chat(
                message = text,
                query = UltraGeneralQueryRouter.classify(text)
            )

            val result = coordinator.answer(route)

            assertTrue(result.handled)
            assertTrue(result.result!!.sensitiveInputBlocked)
            assertEquals(0, calls)
        }
    }

    @Test
    fun comparisonFollowUpKeepsResolvedConversationContextForResearch() {
        val resolved = UltraConversationContextResolver.resolve(
            message = "¿y cuál tiene mejor batería?",
            conversation = listOf(
                "Tú: Ultra, compara el RedMagic 11S Pro con el Galaxy S26 Ultra",
                "Ultra: Estoy comparándolos."
            )
        )

        val request = UltraGeneralQueryRouter.classify(resolved)

        assertEquals(UltraGeneralQueryKind.COMPARISON_RESEARCH, request.kind)
        assertTrue(request.originalText.contains("RedMagic 11S Pro"))
        assertTrue(request.originalText.contains("Galaxy S26 Ultra"))
    }

    private fun fixedProvider(
        id: String,
        claimKey: String,
        value: String,
        text: String,
        authoritative: Boolean
    ): UltraResearchProvider =
        object : UltraResearchProvider {
            override val id = id
            override fun fetch(request: UltraGeneralQueryRequest) =
                UltraResearchEvidence(
                    claimKey = claimKey,
                    value = value,
                    displayText = text,
                    sourceId = id,
                    authoritative = authoritative
                )
        }
}
