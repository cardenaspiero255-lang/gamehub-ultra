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

    @Test
    fun nonRetryableResearchFailureStopsAfterFirstAttempt() {
        var calls = 0
        val gateway = object : UltraResearchGateway {
            override fun answer(request: UltraGeneralQueryRequest): UltraVerifiedResearchResult {
                calls += 1
                return UltraVerifiedResearchResult(
                    message = "No hay proveedores configurados.",
                    confidence = UltraAnswerConfidence.LOW,
                    sources = emptyList(),
                    abstained = true,
                    reasonCode = "NO_PROVIDERS",
                    retryable = false
                )
            }
        }
        val engine = UltraFrontierExecutionEngine(
            coordinator = UltraQueryExecutionCoordinator(gateway)
        )

        val answer = engine.answer(
            UltraGeneralQueryRouter.classify("noticias de Android hoy")
        ) { null }

        assertEquals(1, calls)
        assertTrue(answer.abstained)
        assertFalse(answer.verified)
    }

    @Test
    fun rejectedNonAbstainedResearchNeverLeaksRejectedClaimOrSources() {
        var calls = 0
        val gateway = object : UltraResearchGateway {
            override fun answer(request: UltraGeneralQueryRequest): UltraVerifiedResearchResult {
                calls += 1
                return UltraVerifiedResearchResult(
                    message = "Afirmación no corroborada que no debe mostrarse.",
                    confidence = UltraAnswerConfidence.HIGH,
                    sources = listOf("single-source"),
                    abstained = false,
                    retryable = false
                )
            }
        }
        val frontier = UltraFrontierOrchestrator(
            UltraFrontierPolicy(
                verifiedResearchPassBudget = 1,
                deepResearchPassBudget = 1
            )
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

        assertEquals(1, calls)
        assertTrue(answer.abstained)
        assertFalse(answer.verified)
        assertFalse(answer.message.contains("Afirmación no corroborada"))
        assertTrue(answer.sources.isEmpty())
        assertTrue(answer.message.contains("corrobor", ignoreCase = true))
    }

    @Test
    fun repeatedRetryableEvidenceStopsBeforeExhaustingDeepBudget() {
        var calls = 0
        val gateway = object : UltraResearchGateway {
            override fun answer(request: UltraGeneralQueryRequest): UltraVerifiedResearchResult {
                calls += 1
                return UltraVerifiedResearchResult(
                    message = "Todavía insuficiente.",
                    confidence = UltraAnswerConfidence.LOW,
                    sources = listOf("same-source"),
                    abstained = true,
                    reasonCode = "TEMPORARY_INSUFFICIENT_EVIDENCE",
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

        val answer = engine.answer(
            UltraGeneralQueryRouter.classify(
                "Compara dos teléfonos actuales y dime cuál es mejor"
            )
        ) { null }

        assertEquals(2, calls)
        assertTrue(answer.abstained)
    }

    @Test
    fun networkLossBetweenResearchAttemptsFailsClosedWithoutAnotherProviderCall() {
        var calls = 0
        var networkChecks = 0
        val gateway = object : UltraResearchGateway {
            override fun answer(request: UltraGeneralQueryRequest): UltraVerifiedResearchResult {
                calls += 1
                return UltraVerifiedResearchResult(
                    message = "Evidencia temporalmente insuficiente.",
                    confidence = UltraAnswerConfidence.LOW,
                    sources = emptyList(),
                    abstained = true,
                    retryable = true
                )
            }
        }
        val engine = UltraFrontierExecutionEngine(
            coordinator = UltraQueryExecutionCoordinator(gateway),
            networkAvailable = {
                networkChecks += 1
                networkChecks == 1
            }
        )

        val answer = engine.answer(
            UltraGeneralQueryRouter.classify("noticias de Android hoy")
        ) { "dato local viejo" }

        assertEquals(1, calls)
        assertTrue(answer.abstained)
        assertFalse(answer.verified)
        assertTrue(answer.message.contains("conex", ignoreCase = true))
        assertFalse(answer.message.contains("dato local viejo"))
    }


    @Test
    fun genericLocalFailureIsNeverReturnedAsSuccessfulFallback() {
        var localCalls = 0
        val gateway = object : UltraResearchGateway {
            override fun answer(request: UltraGeneralQueryRequest): UltraVerifiedResearchResult =
                error("stable local knowledge must not call research")
        }
        val engine = UltraFrontierExecutionEngine(
            coordinator = UltraQueryExecutionCoordinator(gateway)
        )

        val answer = engine.answer(
            UltraGeneralQueryRouter.classify("¿Qué es un libro?")
        ) {
            localCalls += 1
            "No pude verificarlo con suficiente confianza."
        }

        assertEquals(2, localCalls)
        assertTrue(answer.abstained)
        assertFalse(answer.verified)
        assertFalse(
            answer.message.contains(
                "no pude verificarlo con suficiente confianza",
                ignoreCase = true
            )
        )
    }

}
