package com.cardenaspiero255.gamehubultra.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiSessionCoachTest {
    @Test
    fun preSessionSummaryUsesReadinessWithoutInventingMissingMetrics() {
        val readiness = GamingReadiness(
            score = 72,
            label = "Aceptable",
            reasons = listOf(
                "Térmica: estado no disponible.",
                "Latencia: no medida.",
                "Batería: nivel intermedio (+2)."
            )
        )

        val summary = AiSessionCoach.preSession(
            readiness = readiness,
            snapshot = SessionCoachSnapshot(
                timestampMillis = 1_000,
                batteryPercent = 48,
                thermalStatus = null,
                thermalHeadroom = null,
                refreshRateHz = null,
                latencyMs = null
            )
        )

        assertTrue(summary.title.contains("72"))
        assertTrue(summary.detail.contains("Aceptable"))
        assertTrue(summary.detail.contains("no disponible") || summary.detail.contains("no medida"))
        assertFalse(summary.detail.contains("120 Hz"))
    }

    @Test
    fun midSessionIgnoresSmallChangesButReportsMeaningfulThermalAndLatencyShift() {
        val previous = SessionCoachSnapshot(
            timestampMillis = 1_000,
            batteryPercent = 80,
            thermalStatus = 1,
            thermalHeadroom = 0.35f,
            refreshRateHz = 120f,
            latencyMs = 35
        )
        val noisy = previous.copy(
            timestampMillis = 2_000,
            batteryPercent = 79,
            thermalHeadroom = 0.38f,
            refreshRateHz = 119f,
            latencyMs = 41
        )
        val meaningful = previous.copy(
            timestampMillis = 3_000,
            batteryPercent = 74,
            thermalStatus = 3,
            thermalHeadroom = 0.84f,
            refreshRateHz = 60f,
            latencyMs = 145
        )

        assertTrue(AiSessionCoach.midSession(previous, noisy).isEmpty())

        val observations = AiSessionCoach.midSession(previous, meaningful)
        assertTrue(observations.any { it.signal == SessionCoachSignal.THERMAL })
        assertTrue(observations.any { it.signal == SessionCoachSignal.LATENCY })
        assertTrue(observations.any { it.signal == SessionCoachSignal.REFRESH })
        assertTrue(observations.any { it.signal == SessionCoachSignal.BATTERY })
    }

    @Test
    fun recurringPatternsRequireRepeatedEvidence() {
        val samples = listOf(
            SessionCoachSnapshot(1_000, 80, 1, 0.30f, 120f, 35),
            SessionCoachSnapshot(2_000, 72, 3, 0.82f, 60f, 135),
            SessionCoachSnapshot(3_000, 64, 3, 0.85f, 60f, 150),
            SessionCoachSnapshot(4_000, 55, 4, 0.88f, 60f, 160)
        )

        val patterns = AiSessionCoach.recurringPatterns(samples)

        assertTrue(patterns.any { it.signal == SessionCoachSignal.THERMAL })
        assertTrue(patterns.any { it.signal == SessionCoachSignal.BATTERY })
        assertTrue(patterns.any { it.signal == SessionCoachSignal.REFRESH })
        assertTrue(patterns.any { it.signal == SessionCoachSignal.LATENCY })
    }

    @Test
    fun postSessionProducesActionableNextStepsWithoutUnsupportedClaims() {
        val samples = listOf(
            SessionCoachSnapshot(1_000, 90, 1, 0.25f, 120f, 30),
            SessionCoachSnapshot(2_000, 80, 3, 0.82f, 60f, 140),
            SessionCoachSnapshot(3_000, 70, 3, 0.86f, 60f, 155),
            SessionCoachSnapshot(4_000, 60, 3, 0.88f, 60f, 150)
        )

        val report = AiSessionCoach.postSession(samples)

        assertTrue(report.summary.isNotBlank())
        assertTrue(report.nextSteps.isNotEmpty())
        assertTrue(report.patterns.size >= 3)
        assertTrue(report.nextSteps.any { it.contains("térmic", ignoreCase = true) })
        assertFalse(report.nextSteps.any { it.contains("forzar", ignoreCase = true) })
        assertEquals(30, report.batteryDropPercent)
    }
}
