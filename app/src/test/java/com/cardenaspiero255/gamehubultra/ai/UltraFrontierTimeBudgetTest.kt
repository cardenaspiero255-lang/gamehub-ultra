package com.cardenaspiero255.gamehubultra.ai

import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UltraFrontierTimeBudgetTest {
    @Test
    fun deepResearchSharesOneGlobalTimeBudgetAcrossRetries() {
        var calls = 0
        var nowNanos = 0L
        val observedTimeouts = mutableListOf<Long>()
        val gateway = object : UltraResearchGateway {
            override fun answer(request: UltraGeneralQueryRequest): UltraVerifiedResearchResult {
                calls += 1
                observedTimeouts += request.timeoutMillis
                nowNanos += TimeUnit.MILLISECONDS.toNanos(
                    if (calls == 1) 70_000L else 20_000L
                )
                return UltraVerifiedResearchResult(
                    message = "Evidencia parcial $calls",
                    confidence = UltraAnswerConfidence.LOW,
                    sources = listOf("source-$calls"),
                    independentSourceCount = 1,
                    abstained = true,
                    retryable = true
                )
            }
        }
        val frontier = UltraFrontierOrchestrator(
            UltraFrontierPolicy(
                deepResearchPassBudget = 3,
                deepResearchTimeBudgetMillis = 90_000L
            )
        )
        val engine = UltraFrontierExecutionEngine(
            coordinator = UltraQueryExecutionCoordinator(gateway),
            frontier = frontier,
            nanoTime = { nowNanos }
        )

        val answer = engine.answer(
            UltraGeneralQueryRouter.classify(
                "Compara dos teléfonos actuales y dime cuál es mejor"
            )
        ) { null }

        assertEquals(2, calls)
        assertEquals(listOf(60_000L, 20_000L), observedTimeouts)
        assertTrue(answer.abstained)
        assertEquals("FRONTIER_TIME_BUDGET_EXHAUSTED", answer.reasonCode)
    }
}
