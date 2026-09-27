package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UltraResearchBoundaryArchitectureTest {

    private val request = UltraGeneralQueryRequest(
        originalText = "precio actual",
        kind = UltraGeneralQueryKind.CURRENT_DATA,
        requiresInternet = true,
        requiresFreshData = true,
        timeoutMillis = 5_000L
    )

    @Test
    fun queryCoordinatorDependsOnResearchBoundaryNotConcreteEngine() {
        val research: UltraResearchGateway = object : UltraResearchGateway {
            override fun answer(request: UltraGeneralQueryRequest) =
                UltraVerifiedResearchResult(
                    message = "dato verificado",
                    confidence = UltraAnswerConfidence.HIGH,
                    sources = listOf("source"),
                    abstained = false
                )
        }

        val answer = UltraQueryExecutionCoordinator(research).answer(
            request = request,
            localChat = { "local" }
        )

        assertEquals("dato verificado", answer.message)
        assertTrue(answer.verified)
    }

    @Test
    fun generalResearchCoordinatorUsesBoundaryWithoutOwningItsLifecycle() {
        var closed = false
        val research = object : UltraResearchGateway {
            override fun answer(request: UltraGeneralQueryRequest) =
                UltraVerifiedResearchResult(
                    message = "dato verificado",
                    confidence = UltraAnswerConfidence.HIGH,
                    sources = listOf("source"),
                    abstained = false
                )

            override fun close() {
                closed = true
            }
        }
        val coordinator = UltraGeneralResearchCoordinator(research)

        val decision = coordinator.answer(
            UltraAgentRoute.Chat(message = request.originalText, query = request)
        )
        coordinator.close()

        assertTrue(decision.handled)
        assertEquals("dato verificado", decision.result?.message)
        assertFalse(closed, "The coordinator must not own/close an injected research gateway")
    }
}
