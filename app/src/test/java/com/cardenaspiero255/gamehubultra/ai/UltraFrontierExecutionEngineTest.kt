package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UltraFrontierExecutionEngineTest {
    @Test
    fun verifiedResearchRetriesWeakEvidenceAndAcceptsSecondStrongAttempt() {
        var calls = 0
        val gateway = object : UltraResearchGateway {
            override fun answer(request: UltraGeneralQueryRequest): UltraVerifiedResearchResult {
                calls += 1
                return if (calls == 1) {
                    UltraVerifiedResearchResult(
                        message = "No pude verificarlo con suficiente confianza.",
                        confidence = UltraAnswerConfidence.LOW,
                        sources = emptyList(),
                        abstained = true,
                        retryable = true
                    )
                } else {
                    UltraVerifiedResearchResult(
                        message = "Dato verificado.",
                        confidence = UltraAnswerConfidence.HIGH,
                        sources = listOf("source-a", "source-b"),
                        abstained = false
                    )
                }
            }
        }
        val engine = UltraFrontierExecutionEngine(
            coordinator = UltraQueryExecutionCoordinator(gateway)
        )
        val request = UltraGeneralQueryRouter.classify("noticias de Android hoy")

        val answer = engine.answer(request) { null }

        assertEquals(2, calls)
        assertEquals("Dato verificado.", answer.message)
        assertTrue(answer.verified)
        assertFalse(answer.abstained)
    }

    @Test
    fun deepResearchStopsAtPolicyBudgetAndKeepsSafeAbstention() {
        var calls = 0
        val gateway = object : UltraResearchGateway {
            override fun answer(request: UltraGeneralQueryRequest): UltraVerifiedResearchResult {
                calls += 1
                return UltraVerifiedResearchResult(
                    message = "No encontré evidencia suficiente.",
                    confidence = UltraAnswerConfidence.LOW,
                    sources = emptyList(),
                    abstained = true,
                    retryable = true
                )
            }
        }
        val frontier = UltraFrontierOrchestrator(
            UltraFrontierPolicy(
                deepResearchPassBudget = 3
            )
        )
        val engine = UltraFrontierExecutionEngine(
            coordinator = UltraQueryExecutionCoordinator(gateway),
            frontier = frontier
        )
        val request = UltraGeneralQueryRouter.classify(
            "Compara dos teléfonos actuales y dime cuál es mejor"
        )

        val answer = engine.answer(request) { null }

        assertEquals(3, calls)
        assertTrue(answer.abstained)
        assertFalse(answer.verified)
    }

    @Test
    fun stableLocalKnowledgeNeverCallsResearchWhenLocalAnswerExists() {
        var calls = 0
        val gateway = object : UltraResearchGateway {
            override fun answer(request: UltraGeneralQueryRequest): UltraVerifiedResearchResult {
                calls += 1
                error("research should not run")
            }
        }
        val engine = UltraFrontierExecutionEngine(
            coordinator = UltraQueryExecutionCoordinator(gateway)
        )
        val request = UltraGeneralQueryRouter.classify("¿Qué es un libro?")

        val answer = engine.answer(request) {
            "Un libro es una obra escrita o visual organizada en páginas o formato digital."
        }

        assertEquals(0, calls)
        assertFalse(answer.abstained)
        assertTrue(answer.message.contains("libro", ignoreCase = true))
    }

    @Test
    fun freshDataWithExplicitOfflineEnvironmentDoesNotCallResearchOrInvent() {
        var calls = 0
        val gateway = object : UltraResearchGateway {
            override fun answer(request: UltraGeneralQueryRequest): UltraVerifiedResearchResult {
                calls += 1
                error("research should be blocked while offline")
            }
        }
        val engine = UltraFrontierExecutionEngine(
            coordinator = UltraQueryExecutionCoordinator(gateway),
            networkAvailable = { false }
        )
        val request = UltraGeneralQueryRouter.classify("precio actual de un teléfono")

        val answer = engine.answer(request) { "Precio antiguo local" }

        assertEquals(0, calls)
        assertTrue(answer.abstained)
        assertFalse(answer.verified)
        assertTrue(answer.message.contains("conex", ignoreCase = true))
        assertFalse(answer.message.contains("Precio antiguo local"))
    }
}
