package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UltraFrontierEvidenceProgressTest {
    @Test
    fun deepResearchStopsWhenRetryOnlyRephrasesSameWeakEvidence() {
        var calls = 0
        val gateway = object : UltraResearchGateway {
            override fun answer(request: UltraGeneralQueryRequest): UltraVerifiedResearchResult {
                calls += 1
                return UltraVerifiedResearchResult(
                    message = "Intento $calls con la misma evidencia insuficiente.",
                    confidence = UltraAnswerConfidence.LOW,
                    sources = listOf("same-source"),
                    abstained = true,
                    reasonCode = "TEMPORARY_INSUFFICIENT_EVIDENCE",
                    retryable = true
                )
            }
        }
        val frontier = UltraFrontierOrchestrator(
            UltraFrontierPolicy(deepResearchPassBudget = 3)
        )
        val engine = UltraFrontierExecutionEngine(
            coordinator = UltraQueryExecutionCoordinator(gateway),
            frontier = frontier
        )

        val answer = engine.answer(
            UltraGeneralQueryRouter.classify(
                "Compara dos teléfonos actuales y dime cuál es mejor"
            )
        ) { null }

        assertEquals(2, calls)
        assertTrue(answer.abstained)
        assertFalse(answer.verified)
        assertEquals("FRONTIER_NO_PROGRESS", answer.reasonCode)
    }

    @Test
    fun deepResearchContinuesWhenRetryAddsIndependentEvidence() {
        var calls = 0
        val gateway = object : UltraResearchGateway {
            override fun answer(request: UltraGeneralQueryRequest): UltraVerifiedResearchResult {
                calls += 1
                return when (calls) {
                    1 -> UltraVerifiedResearchResult(
                        message = "Primera evidencia todavía insuficiente.",
                        confidence = UltraAnswerConfidence.LOW,
                        sources = emptyList(),
                        abstained = true,
                        retryable = true
                    )

                    2 -> UltraVerifiedResearchResult(
                        message = "Segunda evidencia todavía insuficiente.",
                        confidence = UltraAnswerConfidence.LOW,
                        sources = listOf("source-a"),
                        abstained = true,
                        retryable = true
                    )

                    else -> UltraVerifiedResearchResult(
                        message = "Conclusión corroborada.",
                        confidence = UltraAnswerConfidence.HIGH,
                        sources = listOf("source-a", "source-b"),
                        abstained = false
                    )
                }
            }
        }
        val frontier = UltraFrontierOrchestrator(
            UltraFrontierPolicy(deepResearchPassBudget = 3)
        )
        val engine = UltraFrontierExecutionEngine(
            coordinator = UltraQueryExecutionCoordinator(gateway),
            frontier = frontier
        )

        val answer = engine.answer(
            UltraGeneralQueryRouter.classify(
                "Compara dos teléfonos actuales y dime cuál es mejor"
            )
        ) { null }

        assertEquals(3, calls)
        assertFalse(answer.abstained)
        assertTrue(answer.verified)
        assertEquals("Conclusión corroborada.", answer.message)
    }
}
