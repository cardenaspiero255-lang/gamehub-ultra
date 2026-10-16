package com.cardenaspiero255.gamehubultra.ai

import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** EVO V3 Cognition: assertions about externally observable research boundaries. */
class UltraCognitionBudgetAndOfflineSafetyTest {

    @Test
    fun explicitLocalModeCannotBypassFreshPriceVerification() {
        var remoteCalls = 0
        var localCalls = 0
        val coordinator = UltraQueryExecutionCoordinator(object : UltraResearchGateway {
            override fun answer(request: UltraGeneralQueryRequest): UltraVerifiedResearchResult {
                remoteCalls++
                return UltraVerifiedResearchResult(
                    message = "No hay corroboración actual",
                    confidence = UltraAnswerConfidence.LOW,
                    sources = emptyList(),
                    abstained = true,
                    retryable = false
                )
            }
        })

        val answer = coordinator.answer(
            UltraGeneralQueryRequest(
                originalText = "precio actual del teléfono",
                kind = UltraGeneralQueryKind.CURRENT_DATA,
                requiresFreshData = true,
                requiresInternet = false,
                timeoutMillis = 4_000L,
                verificationMode = UltraVerificationMode.LOCAL
            )
        ) {
            localCalls++
            "El precio es 100 (dato antiguo)"
        }

        assertEquals(1, remoteCalls)
        assertEquals(0, localCalls)
        assertFalse(answer.verified)
        assertTrue(answer.abstained)
    }

    @Test
    fun explicitOptionalModeCannotTurnComparisonIntoUnverifiedOfflineAnswer() {
        var localCalls = 0
        var remoteCalls = 0
        val coordinator = UltraQueryExecutionCoordinator(object : UltraResearchGateway {
            override fun answer(request: UltraGeneralQueryRequest): UltraVerifiedResearchResult {
                remoteCalls++
                return UltraVerifiedResearchResult(
                    message = "Comparación verificada",
                    confidence = UltraAnswerConfidence.HIGH,
                    sources = listOf("provider-a", "provider-b"),
                    independentSourceCount = 2,
                    abstained = false
                )
            }
        })
        val answer = coordinator.answer(
            UltraGeneralQueryRequest(
                originalText = "compara A vs B",
                kind = UltraGeneralQueryKind.COMPARISON_RESEARCH,
                requiresInternet = false,
                requiresFreshData = false,
                timeoutMillis = 5_000L,
                verificationMode = UltraVerificationMode.OPTIONAL
            )
        ) {
            localCalls++
            "A es mejor sin fuentes"
        }

        assertEquals(0, localCalls)
        assertEquals(1, remoteCalls)
        assertEquals("Comparación verificada", answer.message)
    }

    @Test
    fun stableOfflineAnswerIsClearlyLabeledNotFreshOrVerified() {
        var researchCalls = 0
        val coordinator = UltraQueryExecutionCoordinator(object : UltraResearchGateway {
            override fun answer(request: UltraGeneralQueryRequest): UltraVerifiedResearchResult {
                researchCalls++
                error("stable offline should not require research")
            }
        })
        val answer = coordinator.answer(
            UltraGeneralQueryRouter.classify("Ultra, ¿qué es un procesador?")
        ) { "Un procesador ejecuta instrucciones." }

        assertEquals(0, researchCalls)
        assertFalse(answer.verified)
        assertTrue(answer.fallbackUsed)
        assertEquals("LOCAL_STABLE_UNVERIFIED", answer.reasonCode)
        assertEquals("offline-stable", answer.stage)
        assertFalse(answer.abstained)
    }

    @Test
    fun researchExceptionReturnsAbstentionWithoutPretendingOfflineAnswerIsFresh() {
        var localCalls = 0
        val coordinator = UltraQueryExecutionCoordinator(object : UltraResearchGateway {
            override fun answer(request: UltraGeneralQueryRequest): UltraVerifiedResearchResult {
                throw IllegalStateException("network engine unavailable")
            }
        })
        val answer = coordinator.answer(
            UltraGeneralQueryRouter.classify("Ultra, precio actual de un teléfono")
        ) {
            localCalls++
            "precio sin confirmar"
        }

        assertEquals(0, localCalls)
        assertTrue(answer.abstained)
        assertFalse(answer.verified)
        assertFalse(answer.retryable)
        assertEquals("RESEARCH_PROVIDER_ERROR", answer.reasonCode)
    }

    @Test
    fun callerProviderCapWinsOverAdaptiveRetryBudgets() {
        val observed = mutableListOf<Int?>()
        val coordinator = UltraQueryExecutionCoordinator(object : UltraResearchGateway {
            override fun answer(request: UltraGeneralQueryRequest): UltraVerifiedResearchResult {
                observed += request.researchProviderBudget
                return UltraVerifiedResearchResult(
                    message = "Dato sin corroborar",
                    confidence = UltraAnswerConfidence.LOW,
                    sources = emptyList(),
                    abstained = true,
                    retryable = true
                )
            }
        })
        val engine = UltraFrontierExecutionEngine(coordinator = coordinator)
        val query = UltraGeneralQueryRouter.classify(
            "Ultra, cuál es el precio actual de un teléfono"
        ).copy(researchProviderBudget = 1)

        val answer = engine.answer(query) { "precio local no verificado" }
        assertTrue(observed.isNotEmpty())
        assertTrue(observed.all { it == 1 }, "Never expand a caller cap: $observed")
        assertTrue(answer.abstained)
        assertFalse(answer.verified)
    }

    @Test
    fun verifiedResearchUsesVerifiedBudgetsEvenWhenRequestModeIsIncorrect() {
        val policy = UltraFrontierPolicy()
        val budget = UltraAdaptiveComputeBudgeter().budget(
            request = UltraGeneralQueryRequest(
                originalText = "que version existe hoy",
                kind = UltraGeneralQueryKind.GENERAL_KNOWLEDGE,
                requiresInternet = true,
                requiresFreshData = true,
                timeoutMillis = 10_000L,
                verificationMode = UltraVerificationMode.LOCAL
            ),
            policy = policy,
            failurePressure = 0,
            worldState = null
        )

        assertEquals(policy.verifiedSourceBudget, budget.sourceBudget)
        assertEquals(policy.verifiedResearchPassBudget, budget.passBudget)
        assertEquals(policy.verifiedResearchTimeBudgetMillis, budget.timeBudgetMillis)
        assertEquals(policy.minimumFreshSources, budget.minimumIndependentSources)
    }

    @Test
    fun offlineFrontierBlocksInconsistentFreshKnowledgeRequestBeforeResearch() {
        var providerCalls = 0
        var localCalls = 0
        val coordinator = UltraQueryExecutionCoordinator(object : UltraResearchGateway {
            override fun answer(request: UltraGeneralQueryRequest): UltraVerifiedResearchResult {
                providerCalls++
                error("research unavailable")
            }
        })
        val engine = UltraFrontierExecutionEngine(
            coordinator = coordinator,
            networkAvailable = { false }
        )
        val query = UltraGeneralQueryRequest(
            originalText = "version actual de una plataforma",
            kind = UltraGeneralQueryKind.GENERAL_KNOWLEDGE,
            requiresInternet = false,
            requiresFreshData = true,
            timeoutMillis = 8_000L,
            verificationMode = UltraVerificationMode.LOCAL
        )

        val answer = engine.answer(query) {
            localCalls++
            "version obsoleta"
        }

        assertEquals(0, providerCalls)
        assertEquals(0, localCalls)
        assertTrue(answer.abstained)
        assertFalse(answer.verified)
        assertEquals("FRONTIER_NETWORK_REQUIRED", answer.reasonCode)
    }

    @Test
    fun exhaustedGlobalResearchBudgetPreventsFirstProviderCall() {
        var nowNanos = 0L
        var providerCalls = 0
        val coordinator = UltraQueryExecutionCoordinator(object : UltraResearchGateway {
            override fun answer(request: UltraGeneralQueryRequest): UltraVerifiedResearchResult {
                providerCalls++
                return UltraVerifiedResearchResult(
                    message = "bad",
                    confidence = UltraAnswerConfidence.LOW,
                    sources = emptyList(),
                    abstained = true
                )
            }
        })
        // Planning happens before the first attempt. If the global research
        // time has elapsed, the first provider must not be contacted.
        val engine = UltraFrontierExecutionEngine(
            coordinator = coordinator,
            nanoTime = {
                nowNanos += TimeUnit.SECONDS.toNanos(50)
                nowNanos
            }
        )
        val result = engine.answer(
            UltraGeneralQueryRouter.classify("Ultra, precio de teléfono hoy")
        ) { "outdated" }

        assertEquals(0, providerCalls)
        assertTrue(result.abstained)
        assertEquals("FRONTIER_TIME_BUDGET_EXHAUSTED", result.reasonCode)
    }
}
