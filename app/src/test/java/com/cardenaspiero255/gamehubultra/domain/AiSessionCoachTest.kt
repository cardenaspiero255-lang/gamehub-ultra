package com.cardenaspiero255.gamehubultra.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiSessionCoachTest {
    @Test
    fun sessionCoachMessageDefaultActionIsNull() {
        val message = SessionCoachMessage(
            signal = SessionCoachSignal.GENERAL,
            priority = SessionCoachPriority.INFO,
            title = "Info",
            detail = "Detalle"
        )

        assertNull(message.action)
    }

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
    @Test
    fun healthyPreSessionStaysInformationalWithoutAction() {
        val result = AiSessionCoach.preSession(
            readiness = GamingReadiness(
                score = 92,
                label = "Listo",
                reasons = listOf(
                    "CPU: 8 o más núcleos lógicos (+10).",
                    "RAM: 8 GB o más (+10)."
                )
            ),
            snapshot = SessionCoachSnapshot(
                timestampMillis = 1L,
                batteryPercent = 88,
                thermalStatus = 1,
                thermalHeadroom = 0.20f,
                refreshRateHz = 120f,
                latencyMs = 30L
            )
        )

        assertEquals(SessionCoachPriority.INFO, result.priority)
        assertNull(result.action)
        assertTrue(result.detail.contains("Sin alertas relevantes"))
    }

    @Test
    fun lowReadinessEscalatesToActionAndDeduplicatesConcernFamilies() {
        val result = AiSessionCoach.preSession(
            readiness = GamingReadiness(
                score = 45,
                label = "Requiere atención",
                reasons = listOf(
                    "Batería: nivel bajo sin carga (-20).",
                    "Térmica: throttling severo o superior (-25).",
                    "Latencia: ≥120 ms (-10).",
                    "Almacenamiento: menos de 10 % libre (-15)."
                )
            ),
            snapshot = SessionCoachSnapshot(
                timestampMillis = 1L,
                batteryPercent = 10,
                thermalStatus = 4,
                thermalHeadroom = 0.90f,
                refreshRateHz = 60f,
                latencyMs = 180L
            )
        )

        assertEquals(SessionCoachPriority.ACTION, result.priority)
        assertTrue(result.action.orEmpty().contains("Revisa"))
        assertTrue(result.detail.contains("Batería baja"))
        assertTrue(result.detail.contains("Estado térmico elevado"))
        assertTrue(result.detail.contains("Latencia elevada"))
    }

    @Test
    fun headroomAloneCanTriggerThermalObservation() {
        val previous = SessionCoachSnapshot(
            timestampMillis = 1L,
            batteryPercent = null,
            thermalStatus = null,
            thermalHeadroom = null,
            refreshRateHz = null,
            latencyMs = null
        )
        val current = previous.copy(
            timestampMillis = 2L,
            thermalHeadroom = 0.90f
        )

        val messages = AiSessionCoach.midSession(previous, current)

        assertEquals(1, messages.size)
        assertEquals(SessionCoachSignal.THERMAL, messages.single().signal)
        assertEquals(SessionCoachPriority.ACTION, messages.single().priority)
    }

    @Test
    fun lowBatteryCanTriggerEvenWithoutFivePointDrop() {
        val previous = SessionCoachSnapshot(1L, 16, 1, 0.2f, 60f, 40L)
        val current = SessionCoachSnapshot(2L, 14, 1, 0.2f, 60f, 40L)

        val battery = AiSessionCoach.midSession(previous, current)
            .single { it.signal == SessionCoachSignal.BATTERY }

        assertEquals(SessionCoachPriority.ACTION, battery.priority)
        assertTrue(battery.title.contains("Batería baja"))
    }

    @Test
    fun latencyCanTriggerWhenNoPreviousMeasurementExists() {
        val previous = SessionCoachSnapshot(1L, 80, 1, 0.2f, 120f, null)
        val current = previous.copy(timestampMillis = 2L, latencyMs = 150L)

        val latency = AiSessionCoach.midSession(previous, current)
            .single { it.signal == SessionCoachSignal.LATENCY }

        assertTrue(latency.detail.contains("150 ms"))
    }

    @Test
    fun missingOrNonMeaningfulSignalsDoNotCreateFalseAlerts() {
        val previous = SessionCoachSnapshot(1L, null, null, Float.NaN, null, null)
        val current = SessionCoachSnapshot(2L, null, null, 0.79f, 60f, 100L)

        assertTrue(AiSessionCoach.midSession(previous, current).isEmpty())

        val lowRefreshBaseline = SessionCoachSnapshot(1L, 80, 1, 0.2f, 60f, 30L)
        val lowerRefresh = lowRefreshBaseline.copy(timestampMillis = 2L, refreshRateHz = 30f)
        assertTrue(
            AiSessionCoach.midSession(lowRefreshBaseline, lowerRefresh)
                .none { it.signal == SessionCoachSignal.REFRESH }
        )
    }

    @Test
    fun recurringPatternsNeedAtLeastThreeSamplesAndIgnoreHealthyEvidence() {
        val tooShort = listOf(
            SessionCoachSnapshot(1L, 90, 1, 0.2f, 120f, 30L),
            SessionCoachSnapshot(2L, 89, 1, 0.2f, 120f, 31L)
        )
        assertTrue(AiSessionCoach.recurringPatterns(tooShort).isEmpty())

        val healthy = listOf(
            SessionCoachSnapshot(3L, 90, 1, 0.2f, 120f, 30L),
            SessionCoachSnapshot(1L, 92, 1, 0.2f, 120f, 35L),
            SessionCoachSnapshot(2L, 91, 1, 0.2f, 120f, 32L)
        )
        assertTrue(AiSessionCoach.recurringPatterns(healthy).isEmpty())
    }

    @Test
    fun emptyPostSessionReportIsExplicitAndSafe() {
        val report = AiSessionCoach.postSession(emptyList())

        assertTrue(report.summary.contains("No hubo telemetría suficiente"))
        assertTrue(report.nextSteps.isEmpty())
        assertTrue(report.patterns.isEmpty())
        assertNull(report.batteryDropPercent)
    }

    @Test
    fun postSessionWithoutBatteryStillProducesStableSummary() {
        val report = AiSessionCoach.postSession(
            listOf(
                SessionCoachSnapshot(2L, null, 1, 0.2f, 120f, 30L),
                SessionCoachSnapshot(1L, null, 1, 0.2f, 120f, 35L),
                SessionCoachSnapshot(3L, null, 1, 0.2f, 120f, 32L)
            )
        )

        assertNull(report.batteryDropPercent)
        assertTrue(report.summary.contains("sin patrones repetidos relevantes"))
    }

}
