package com.cardenaspiero255.gamehubultra.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThermalPredictionEngineTest {
    private val policy = ThermalPredictionPolicy(
        minimumSamples = 5,
        minimumWindowMillis = 40_000L,
        maximumWindowMillis = 120_000L,
        risingSlopePerMinute = 0.06f,
        fastRisingSlopePerMinute = 0.14f,
        highRiskHeadroom = 0.72f,
        criticalRiskHeadroom = 0.82f,
        minimumPreventiveConfidence = 0.78f,
        noiseTolerance = 0.04f,
        minimumRisingFraction = 0.70f,
        recoveryHeadroom = 0.58f,
        recoverySlopePerMinute = -0.04f,
        accelerationThresholdPerMinuteSquared = 0.04f,
        missingHeadroomConfidencePenalty = 0.25f,
    )

    @Test
    fun `stable recent headroom stays low risk and does not request prevention`() {
        val result = ThermalPredictionEngine(policy).predict(
            samples = samples(0.40f, 0.41f, 0.40f, 0.42f, 0.41f),
        )

        assertEquals(ThermalTrend.STABLE, result.trend)
        assertEquals(ThermalRisk.LOW, result.risk)
        assertFalse(result.allowPreventiveSignal)
        assertTrue(result.confidence >= 0.50f)
    }

    @Test
    fun `sustained rising headroom produces high risk preventive evidence`() {
        val result = ThermalPredictionEngine(policy).predict(
            samples = samples(0.46f, 0.52f, 0.59f, 0.66f, 0.74f),
        )

        assertTrue(result.trend == ThermalTrend.RISING || result.trend == ThermalTrend.RISING_FAST)
        assertEquals(ThermalRisk.HIGH, result.risk)
        assertTrue(result.confidence >= policy.minimumPreventiveConfidence)
        assertTrue(result.allowPreventiveSignal)
        assertTrue(result.evidence.any { it.kind == ThermalEvidenceKind.PREDICTED_TREND })
        assertTrue(result.evidence.any { it.kind == ThermalEvidenceKind.MEASURED_HEADROOM })
    }

    @Test
    fun `single noisy spike never becomes high risk prediction`() {
        val result = ThermalPredictionEngine(policy).predict(
            samples = samples(0.44f, 0.45f, 0.78f, 0.46f, 0.47f),
        )

        assertFalse(result.risk == ThermalRisk.HIGH || result.risk == ThermalRisk.CRITICAL)
        assertFalse(result.allowPreventiveSignal)
    }

    @Test
    fun `missing headroom reduces confidence and status fallback never invents headroom`() {
        val result = ThermalPredictionEngine(policy).predict(
            samples = statusOnlySamples(1, 1, 2, 2, 2),
        )

        assertEquals(ThermalSignalMode.STATUS_ONLY, result.signalMode)
        assertTrue(result.confidence < policy.minimumPreventiveConfidence)
        assertFalse(result.allowPreventiveSignal)
        assertTrue(result.evidence.none { it.kind == ThermalEvidenceKind.MEASURED_HEADROOM })
    }

    @Test
    fun `measured severe status is critical evidence without being labelled a prediction`() {
        val result = ThermalPredictionEngine(policy).predict(
            samples = statusOnlySamples(1, 2, 2, 3, 3),
        )

        assertEquals(ThermalRisk.CRITICAL, result.risk)
        assertTrue(result.evidence.any {
            it.kind == ThermalEvidenceKind.MEASURED_STATUS && it.measured
        })
        assertTrue(result.evidence.filter { it.kind == ThermalEvidenceKind.MEASURED_STATUS }.all { !it.predicted })
    }

    @Test
    fun `cooling window reports recovery and does not request preventive downgrade`() {
        val result = ThermalPredictionEngine(policy).predict(
            samples = samples(0.72f, 0.67f, 0.63f, 0.57f, 0.52f),
        )

        assertEquals(ThermalTrend.COOLING, result.trend)
        assertTrue(result.recovering)
        assertFalse(result.allowPreventiveSignal)
    }

    @Test
    fun `insufficient samples are unknown and never actionable`() {
        val result = ThermalPredictionEngine(policy).predict(
            samples = samples(0.55f, 0.63f, 0.71f),
        )

        assertEquals(ThermalTrend.UNKNOWN, result.trend)
        assertEquals(ThermalRisk.UNKNOWN, result.risk)
        assertFalse(result.allowPreventiveSignal)
    }

    @Test
    fun `policy rejects unsafe calibration shapes instead of hiding magic values`() {
        var rejected = false
        try {
            ThermalPredictionPolicy(
                minimumSamples = 1,
                minimumWindowMillis = 40_000L,
                maximumWindowMillis = 20_000L,
            )
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
    }

    private fun samples(vararg headroom: Float): List<SessionCoachSnapshot> =
        headroom.mapIndexed { index, value ->
            SessionCoachSnapshot(
                timestampMillis = index * 10_000L,
                batteryPercent = 80,
                thermalStatus = if (value >= 0.72f) 2 else 1,
                thermalHeadroom = value,
                refreshRateHz = 120f,
                latencyMs = 30L,
            )
        }

    private fun statusOnlySamples(vararg status: Int): List<SessionCoachSnapshot> =
        status.mapIndexed { index, value ->
            SessionCoachSnapshot(
                timestampMillis = index * 10_000L,
                batteryPercent = 80,
                thermalStatus = value,
                thermalHeadroom = null,
                refreshRateHz = 120f,
                latencyMs = 30L,
            )
        }
    @Test
    fun `preventive boundary is centralized in policy and exact confidence limit is inclusive`() {
        val calibrated = policy.copy(
            minimumPreventiveConfidence = 0.78f,
            preventiveRiskThreshold = ThermalRisk.HIGH,
            preventiveTrends = setOf(ThermalTrend.RISING, ThermalTrend.RISING_FAST),
        )

        assertTrue(
            calibrated.allowsPreventiveSignal(
                risk = ThermalRisk.HIGH,
                trend = ThermalTrend.RISING,
                confidence = 0.78f,
            )
        )
        assertFalse(
            calibrated.allowsPreventiveSignal(
                risk = ThermalRisk.HIGH,
                trend = ThermalTrend.RISING,
                confidence = 0.779f,
            )
        )
        assertFalse(
            calibrated.allowsPreventiveSignal(
                risk = ThermalRisk.MODERATE,
                trend = ThermalTrend.RISING,
                confidence = 1f,
            )
        )
        assertFalse(
            calibrated.allowsPreventiveSignal(
                risk = ThermalRisk.HIGH,
                trend = ThermalTrend.STABLE,
                confidence = 1f,
            )
        )
    }

    @Test
    fun `preventive risk threshold can be recalibrated without rewriting engine`() {
        val calibrated = policy.copy(
            risingSlopePerMinute = 0.05f,
            fastRisingSlopePerMinute = 0.20f,
            highRiskHeadroom = 0.90f,
            criticalRiskHeadroom = 1.00f,
            minimumPreventiveConfidence = 0.50f,
            noiseTolerance = 0.01f,
            preventiveRiskThreshold = ThermalRisk.MODERATE,
            preventiveTrends = setOf(ThermalTrend.RISING, ThermalTrend.RISING_FAST),
        )

        val result = ThermalPredictionEngine(calibrated).predict(
            samples = samples(0.40f, 0.42f, 0.44f, 0.46f, 0.49f),
        )

        assertEquals(ThermalTrend.RISING, result.trend)
        assertEquals(ThermalRisk.MODERATE, result.risk)
        assertTrue(result.allowPreventiveSignal)
    }


}
