package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UltraFrontierAuditTrailTest {
    @Test
    fun auditKeepsOnlyBoundedStructuredMetadataWithoutPromptContent() {
        var now = 100L
        val audit = UltraFrontierAuditTrail(
            capacity = 2,
            nowMillis = { now++ }
        )

        audit.record(
            correlationId = "corr-1",
            lane = UltraFrontierLane.VERIFIED_RESEARCH,
            event = UltraFrontierAuditEvent.RESEARCH_ATTEMPT,
            attempt = 1,
            reasonCode = "FIRST_PASS"
        )
        audit.record(
            correlationId = "corr-1",
            lane = UltraFrontierLane.VERIFIED_RESEARCH,
            event = UltraFrontierAuditEvent.RETRY,
            attempt = 2,
            reasonCode = "LOW_CONFIDENCE"
        )
        audit.record(
            correlationId = "corr-1",
            lane = UltraFrontierLane.VERIFIED_RESEARCH,
            event = UltraFrontierAuditEvent.ACCEPT,
            attempt = 2,
            reasonCode = "VERIFIED"
        )

        val snapshot = audit.snapshot()

        assertEquals(2, snapshot.size)
        assertEquals(UltraFrontierAuditEvent.RETRY, snapshot.first().event)
        assertEquals(UltraFrontierAuditEvent.ACCEPT, snapshot.last().event)
        assertTrue(snapshot.all { it.correlationId == "corr-1" })
        assertFalse(snapshot.toString().contains("prompt", ignoreCase = true))
    }

    @Test
    fun executionEngineEmitsPlanRetryAndAcceptEvents() {
        var calls = 0
        val gateway = object : UltraResearchGateway {
            override fun answer(request: UltraGeneralQueryRequest): UltraVerifiedResearchResult {
                calls += 1
                return if (calls == 1) {
                    UltraVerifiedResearchResult(
                        message = "Sin evidencia suficiente.",
                        confidence = UltraAnswerConfidence.LOW,
                        sources = emptyList(),
                        abstained = true,
                        retryable = true
                    )
                } else {
                    UltraVerifiedResearchResult(
                        message = "Respuesta corroborada.",
                        confidence = UltraAnswerConfidence.HIGH,
                        sources = listOf("a", "b"),
                        abstained = false
                    )
                }
            }
        }
        val audit = UltraFrontierAuditTrail()
        val engine = UltraFrontierExecutionEngine(
            coordinator = UltraQueryExecutionCoordinator(gateway),
            auditTrail = audit
        )
        val request = UltraGeneralQueryRouter.classify("noticias de Android hoy")

        val answer = engine.answer(request) { null }
        val events = audit.snapshot().map { it.event }

        assertTrue(answer.verified)
        assertEquals(2, calls)
        assertTrue(UltraFrontierAuditEvent.PLAN_CREATED in events)
        assertTrue(UltraFrontierAuditEvent.RESEARCH_ATTEMPT in events)
        assertTrue(UltraFrontierAuditEvent.RETRY in events)
        assertTrue(UltraFrontierAuditEvent.ACCEPT in events)
    }

    @Test
    fun blockedFreshQueryIsAuditedAsAbstentionWithoutExecutingResearch() {
        var calls = 0
        val gateway = object : UltraResearchGateway {
            override fun answer(request: UltraGeneralQueryRequest): UltraVerifiedResearchResult {
                calls += 1
                error("must not execute")
            }
        }
        val audit = UltraFrontierAuditTrail()
        val engine = UltraFrontierExecutionEngine(
            coordinator = UltraQueryExecutionCoordinator(gateway),
            networkAvailable = { false },
            auditTrail = audit
        )
        val request = UltraGeneralQueryRouter.classify("precio actual del teléfono")

        val answer = engine.answer(request) { "dato viejo" }

        assertTrue(answer.abstained)
        assertEquals(0, calls)
        assertTrue(
            audit.snapshot().any {
                it.event == UltraFrontierAuditEvent.ABSTAIN &&
                    it.reasonCode == "FRONTIER_NETWORK_REQUIRED"
            }
        )
    }
}
