package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UltraFrontierIndependentEvidenceTest {
    @Test
    fun freshResearchDoesNotTreatMultipleUrlsAsIndependentSources() {
        val gateway = object : UltraResearchGateway {
            override fun answer(request: UltraGeneralQueryRequest): UltraVerifiedResearchResult =
                UltraVerifiedResearchResult(
                    message = "Dato candidato.",
                    confidence = UltraAnswerConfidence.HIGH,
                    sources = listOf("url-a", "url-b", "url-c"),
                    independentSourceCount = 1,
                    abstained = false
                )
        }
        val frontier = UltraFrontierOrchestrator(
            UltraFrontierPolicy(
                minimumFreshSources = 2,
                verifiedResearchPassBudget = 1
            )
        )
        val engine = UltraFrontierExecutionEngine(
            coordinator = UltraQueryExecutionCoordinator(gateway),
            frontier = frontier
        )

        val answer = engine.answer(
            UltraGeneralQueryRouter.classify("noticias de Android hoy")
        ) { null }

        assertTrue(answer.abstained)
        assertFalse(answer.verified)
        assertTrue(answer.sources.isEmpty())
    }

    @Test
    fun freshResearchAcceptsExplicitIndependentSourceQuorum() {
        val gateway = object : UltraResearchGateway {
            override fun answer(request: UltraGeneralQueryRequest): UltraVerifiedResearchResult =
                UltraVerifiedResearchResult(
                    message = "Dato corroborado.",
                    confidence = UltraAnswerConfidence.HIGH,
                    sources = listOf("url-a", "url-b"),
                    independentSourceCount = 2,
                    abstained = false
                )
        }
        val frontier = UltraFrontierOrchestrator(
            UltraFrontierPolicy(
                minimumFreshSources = 2,
                verifiedResearchPassBudget = 1
            )
        )
        val engine = UltraFrontierExecutionEngine(
            coordinator = UltraQueryExecutionCoordinator(gateway),
            frontier = frontier
        )

        val answer = engine.answer(
            UltraGeneralQueryRouter.classify("noticias de Android hoy")
        ) { null }

        assertFalse(answer.abstained)
        assertTrue(answer.verified)
    }
}
