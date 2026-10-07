package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UltraFrontierResearchBudgetTest {
    @Test
    fun frontierExecutionPropagatesPlanSourceBudgetIntoResearchRequest() {
        var receivedBudget: Int? = null
        val gateway = object : UltraResearchGateway {
            override fun answer(request: UltraGeneralQueryRequest): UltraVerifiedResearchResult {
                receivedBudget = request.researchProviderBudget
                return UltraVerifiedResearchResult(
                    message = "verificado",
                    confidence = UltraAnswerConfidence.HIGH,
                    sources = listOf("a", "b"),
                    abstained = false
                )
            }
        }
        val frontier = UltraFrontierOrchestrator(
            UltraFrontierPolicy(
                verifiedSourceBudget = 7,
                deepSourceBudget = 11
            )
        )
        val engine = UltraFrontierExecutionEngine(
            coordinator = UltraQueryExecutionCoordinator(gateway),
            frontier = frontier
        )

        engine.answer(
            UltraGeneralQueryRouter.classify("noticias de Android hoy")
        ) { null }

        assertEquals(7, receivedBudget)
    }

    @Test
    fun verifiedResearchEngineNeverStartsProvidersBeyondRequestBudget() {
        val calls = mutableListOf<String>()
        val providers = (0 until 6).map { index ->
            object : UltraResearchProvider {
                override val id: String = "provider-$index"

                override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence {
                    synchronized(calls) { calls += id }
                    return UltraResearchEvidence(
                        claimKey = "same",
                        value = "same",
                        displayText = "same answer",
                        sourceId = id,
                        independentSourceCount = 2
                    )
                }
            }
        }
        val engine = UltraVerifiedResearchEngine(providers = providers)
        val request = UltraGeneralQueryRouter
            .classify("noticias de Android hoy")
            .copy(researchProviderBudget = 2)

        val result = engine.answer(request)

        assertTrue(!result.abstained)
        assertEquals(2, calls.distinct().size)
        assertTrue(calls.all { it == "provider-0" || it == "provider-1" })
    }
}
