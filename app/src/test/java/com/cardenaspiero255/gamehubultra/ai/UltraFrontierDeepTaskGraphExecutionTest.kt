package com.cardenaspiero255.gamehubultra.ai

import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UltraFrontierDeepTaskGraphExecutionTest {
    @Test
    fun deepResearchExecutesDiversifiedResearchTasksFromThePlan() {
        val partitions = Collections.synchronizedList(
            mutableListOf<Pair<Int, Int?>>()
        )
        val gateway = object : UltraResearchGateway {
            override val supportsProviderPartitioning: Boolean = true

            override fun answer(request: UltraGeneralQueryRequest): UltraVerifiedResearchResult {
                partitions +=
                    request.researchProviderOffset to request.researchProviderBudget
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

        val sorted = partitions.sortedBy { it.first }
        assertTrue(sorted.size >= 2)
        assertEquals(0, sorted.first().first)
        val firstBudget = sorted.first().second ?: 0
        assertTrue(firstBudget > 0)
        assertTrue(sorted[1].first >= sorted[0].first + firstBudget)
        assertFalse(answer.abstained)
        assertTrue(answer.verified)
        assertEquals("Candidato A", answer.message)
    }

    @Test
    fun frontierV2DeepResearchNeverOverspendsPlannedSourceBudget() {
        val partitions = Collections.synchronizedList(
            mutableListOf<Pair<Int, Int>>()
        )
        val gateway = object : UltraResearchGateway {
            override val supportsProviderPartitioning: Boolean = true

            override fun answer(request: UltraGeneralQueryRequest): UltraVerifiedResearchResult {
                val budget = request.researchProviderBudget ?: 0
                partitions += request.researchProviderOffset to budget
                return UltraVerifiedResearchResult(
                    message = "Mismo candidato",
                    confidence = UltraAnswerConfidence.HIGH,
                    sources = listOf("source-" + request.researchProviderOffset),
                    independentSourceCount = 2,
                    abstained = false
                )
            }
        }
        val evolution = UltraFrontierEvolutionController()
        val frontier = UltraFrontierOrchestrator(
            policy = UltraFrontierPolicy(
                verifiedSourceBudget = 6,
                deepSourceBudget = 7
            ),
            evolution = evolution
        )
        val engine = UltraFrontierExecutionEngine(
            coordinator = UltraQueryExecutionCoordinator(gateway),
            evolution = evolution,
            frontier = frontier
        )

        val answer = engine.answer(
            UltraGeneralQueryRouter.classify(
                "Compara profundamente dos teléfonos actuales"
            )
        ) { null }

        val firstAttempt = partitions
            .sortedBy { it.first }
            .take(2)

        assertEquals(2, firstAttempt.size)
        assertEquals(7, firstAttempt.sumOf { it.second })
        assertTrue(firstAttempt[1].first >= firstAttempt[0].first + firstAttempt[0].second)
        assertFalse(answer.abstained)
    }

}
