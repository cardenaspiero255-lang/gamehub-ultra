package com.cardenaspiero255.gamehubultra.ai

import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UltraFrontierDeepTaskGraphExecutionTest {
    @Test
    fun deepResearchExecutesDiversifiedResearchTasksFromThePlan() {
        val offsets = Collections.synchronizedList(mutableListOf<Int>())
        val gateway = object : UltraResearchGateway {
            override fun answer(request: UltraGeneralQueryRequest): UltraVerifiedResearchResult {
                offsets += request.researchProviderOffset
                return if (request.researchProviderOffset == 0) {
                    UltraVerifiedResearchResult(
                        message = "Candidato A",
                        confidence = UltraAnswerConfidence.HIGH,
                        sources = listOf("a", "b"),
                        independentSourceCount = 2,
                        abstained = false
                    )
                } else {
                    UltraVerifiedResearchResult(
                        message = "Candidato B",
                        confidence = UltraAnswerConfidence.MEDIUM,
                        sources = listOf("c"),
                        independentSourceCount = 1,
                        abstained = false
                    )
                }
            }
        }
        val evolution = UltraFrontierEvolutionController()
        val engine = UltraFrontierExecutionEngine(
            coordinator = UltraQueryExecutionCoordinator(gateway),
            evolution = evolution,
            frontier = UltraFrontierOrchestrator(evolution = evolution)
        )

        val answer = engine.answer(
            UltraGeneralQueryRouter.classify(
                "Compara dos teléfonos actuales y dime cuál es mejor"
            )
        ) { null }

        assertEquals(setOf(0, 1), offsets.toSet())
        assertTrue(offsets.size >= 2)
        assertFalse(answer.abstained)
        assertTrue(answer.verified)
        assertEquals("Candidato A", answer.message)
    }
}
