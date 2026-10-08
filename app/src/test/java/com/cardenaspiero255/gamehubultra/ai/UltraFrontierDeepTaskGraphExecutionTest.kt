package com.cardenaspiero255.gamehubultra.ai

import java.util.Collections
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
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
    fun constrainedFrontierV2FallsBackToSingleResearchWorker() {
        UltraFrontierWorldStateRegistry.clear()
        val requests = Collections.synchronizedList(
            mutableListOf<Pair<Int, Int?>>()
        )
        try {
            UltraFrontierWorldStateRegistry.update(
                UltraFrontierWorldState(
                    selectedGamePackage = "test.game",
                    sessionActive = true,
                    selectedProfile = PerformanceProfile.BALANCED,
                    networkValidated = true,
                    networkLatencyMs = 40L,
                    batteryPercent = 12,
                    charging = false,
                    thermalStatus = null,
                    thermalHeadroom = null,
                    thermalTrend = null,
                    thermalRisk = null,
                    thermalConfidence = null,
                    batteryRecommendation = null,
                    preventAggressiveProfiles = true,
                    adaptiveScore = null,
                    timestampMillis = System.currentTimeMillis()
                )
            )
            val gateway = object : UltraResearchGateway {
                override val supportsProviderPartitioning: Boolean = true

                override fun answer(
                    request: UltraGeneralQueryRequest
                ): UltraVerifiedResearchResult {
                    requests += request.researchProviderOffset to request.researchProviderBudget
                    return UltraVerifiedResearchResult(
                        message = "Respuesta verificada con uso prudente.",
                        confidence = UltraAnswerConfidence.HIGH,
                        sources = listOf("official-a", "official-b"),
                        independentSourceCount = 2,
                        abstained = false
                    )
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
                    "Compara profundamente dos teléfonos actuales"
                )
            ) { null }

            assertTrue(answer.verified)
            assertFalse(answer.abstained)
            assertEquals(1, requests.size)
            assertEquals(0, requests.single().first)
            assertTrue((requests.single().second ?: 0) >= 2)
        } finally {
            UltraFrontierWorldStateRegistry.clear()
        }
    }

    @Test
    fun constrainedRetryUsesFreshProviderPartitionRatherThanRepeatingCachedFailure() {
        UltraFrontierWorldStateRegistry.clear()
        val offsets = Collections.synchronizedList(mutableListOf<Int>())
        try {
            UltraFrontierWorldStateRegistry.update(
                UltraFrontierWorldState(
                    selectedGamePackage = "constrained.game",
                    sessionActive = true,
                    selectedProfile = PerformanceProfile.BALANCED,
                    networkValidated = true,
                    networkLatencyMs = 40L,
                    batteryPercent = 12,
                    charging = false,
                    thermalStatus = null,
                    thermalHeadroom = null,
                    thermalTrend = null,
                    thermalRisk = null,
                    thermalConfidence = null,
                    batteryRecommendation = null,
                    preventAggressiveProfiles = true,
                    adaptiveScore = null,
                    timestampMillis = System.currentTimeMillis()
                )
            )
            val gateway = object : UltraResearchGateway {
                override val supportsProviderPartitioning: Boolean = true

                override fun answer(
                    request: UltraGeneralQueryRequest
                ): UltraVerifiedResearchResult {
                    offsets += request.researchProviderOffset
                    return if (request.researchProviderOffset < 7) {
                        UltraVerifiedResearchResult(
                            message = "No hay información suficiente.",
                            confidence = UltraAnswerConfidence.LOW,
                            sources = emptyList(),
                            independentSourceCount = 0,
                            abstained = true,
                            retryable = true,
                            reasonCode = "CONSTRAINED_PARTITION_EXHAUSTED"
                        )
                    } else {
                        UltraVerifiedResearchResult(
                            message = "Información recuperada de nuevas fuentes.",
                            confidence = UltraAnswerConfidence.HIGH,
                            sources = listOf("official-a", "official-b"),
                            independentSourceCount = 2,
                            abstained = false
                        )
                    }
                }
            }
            val evolution = UltraFrontierEvolutionController()
            val engine = UltraFrontierExecutionEngine(
                coordinator = UltraQueryExecutionCoordinator(gateway),
                evolution = evolution,
                frontier = UltraFrontierOrchestrator(
                    policy = UltraFrontierPolicy(
                        verifiedSourceBudget = 6,
                        deepSourceBudget = 7,
                        deepResearchPassBudget = 2
                    ),
                    evolution = evolution
                )
            )
            val answer = engine.answer(
                UltraGeneralQueryRouter.classify(
                    "Compara profundamente dos teléfonos actuales"
                )
            ) { null }
            assertEquals(listOf(0, 7), offsets.toList())
            assertTrue(answer.verified)
            assertFalse(answer.abstained)
        } finally {
            UltraFrontierWorldStateRegistry.clear()
        }
    }

    @Test
    fun constrainedRetryWithTwoActualProvidersUsesInRangeNextSlot() {
        UltraFrontierWorldStateRegistry.clear()
        val offsets = Collections.synchronizedList(mutableListOf<Int>())
        try {
            UltraFrontierWorldStateRegistry.update(
                UltraFrontierWorldState(
                    selectedGamePackage = "constrained.game",
                    sessionActive = true,
                    selectedProfile = PerformanceProfile.BALANCED,
                    networkValidated = true,
                    networkLatencyMs = 40L,
                    batteryPercent = 12,
                    charging = false,
                    thermalStatus = null,
                    thermalHeadroom = null,
                    thermalTrend = null,
                    thermalRisk = null,
                    thermalConfidence = null,
                    batteryRecommendation = null,
                    preventAggressiveProfiles = true,
                    adaptiveScore = null,
                    timestampMillis = System.currentTimeMillis()
                )
            )
            val gateway = object : UltraResearchGateway {
                override val supportsProviderPartitioning: Boolean = true
                override val providerPartitionCapacity: Int = 2

                override fun answer(
                    request: UltraGeneralQueryRequest
                ): UltraVerifiedResearchResult {
                    offsets += request.researchProviderOffset
                    return if (request.researchProviderOffset == 0) {
                        UltraVerifiedResearchResult(
                            message = "No hay información suficiente.",
                            confidence = UltraAnswerConfidence.LOW,
                            sources = emptyList(),
                            independentSourceCount = 0,
                            abstained = true,
                            retryable = true,
                            reasonCode = "CONSTRAINED_PARTITION_EXHAUSTED"
                        )
                    } else {
                        UltraVerifiedResearchResult(
                            message = "Información recuperada de nuevas fuentes.",
                            confidence = UltraAnswerConfidence.HIGH,
                            sources = listOf("official-a", "official-b"),
                            independentSourceCount = 2,
                            abstained = false
                        )
                    }
                }
            }
            val evolution = UltraFrontierEvolutionController()
            val engine = UltraFrontierExecutionEngine(
                coordinator = UltraQueryExecutionCoordinator(gateway),
                evolution = evolution,
                frontier = UltraFrontierOrchestrator(
                    policy = UltraFrontierPolicy(
                        verifiedSourceBudget = 6,
                        deepSourceBudget = 7,
                        deepResearchPassBudget = 2
                    ),
                    evolution = evolution
                )
            )
            val answer = engine.answer(
                UltraGeneralQueryRouter.classify(
                    "Compara profundamente dos teléfonos actuales y verifica nuevas fuentes"
                )
            ) { null }
            assertEquals(listOf(0, 1), offsets.toList())
            assertTrue(answer.verified)
            assertFalse(answer.abstained)
        } finally {
            UltraFrontierWorldStateRegistry.clear()
        }
    }

    @Test
    fun frontierV2DeepResearchNeverOverspendsPlannedSourceBudget() {
        UltraFrontierWorldStateRegistry.clear()
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
        UltraFrontierWorldStateRegistry.clear()
    }


    @Test
    fun frontierV2RetryReplansOntoFreshProviderPartitions() {
        UltraFrontierWorldStateRegistry.clear()
        val offsets = Collections.synchronizedList(mutableListOf<Int>())
        val audit = UltraFrontierAuditTrail()
        val gateway = object : UltraResearchGateway {
            override val supportsProviderPartitioning: Boolean = true

            override fun answer(request: UltraGeneralQueryRequest): UltraVerifiedResearchResult {
                offsets += request.researchProviderOffset
                return if (request.researchProviderOffset < 7) {
                    UltraVerifiedResearchResult(
                        message = "Evidencia insuficiente.",
                        confidence = UltraAnswerConfidence.LOW,
                        sources = emptyList(),
                        independentSourceCount = 0,
                        abstained = true,
                        retryable = true,
                        reasonCode = "V2_BRANCH_WEAK"
                    )
                } else {
                    UltraVerifiedResearchResult(
                        message = "Respuesta recuperada.",
                        confidence = UltraAnswerConfidence.HIGH,
                        sources = listOf("source-" + request.researchProviderOffset),
                        independentSourceCount = 2,
                        abstained = false
                    )
                }
            }
        }
        val evolution = UltraFrontierEvolutionController()
        val frontier = UltraFrontierOrchestrator(
            policy = UltraFrontierPolicy(
                verifiedSourceBudget = 6,
                deepSourceBudget = 7,
                deepResearchPassBudget = 2
            ),
            evolution = evolution
        )
        val engine = UltraFrontierExecutionEngine(
            coordinator = UltraQueryExecutionCoordinator(gateway),
            evolution = evolution,
            frontier = frontier,
            auditTrail = audit
        )

        val answer = engine.answer(
            UltraGeneralQueryRouter.classify(
                "Compara profundamente dos teléfonos actuales"
            )
        ) { null }

        val observed = offsets.toList()
        assertEquals(4, observed.size)
        assertTrue(observed.take(2).all { it < 7 })
        assertTrue(observed.drop(2).all { it >= 7 })
        assertTrue(answer.verified)
        assertFalse(answer.abstained)
        assertTrue(
            audit.snapshot().any {
                it.event == UltraFrontierAuditEvent.REPLAN &&
                    it.attempt == 2
            }
        )
        UltraFrontierWorldStateRegistry.clear()
    }

}
