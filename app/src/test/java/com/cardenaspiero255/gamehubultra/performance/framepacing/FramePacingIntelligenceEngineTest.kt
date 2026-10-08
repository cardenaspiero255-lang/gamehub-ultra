package com.cardenaspiero255.gamehubultra.performance.framepacing

import com.cardenaspiero255.gamehubultra.domain.BatteryAwareGamingEngine
import com.cardenaspiero255.gamehubultra.domain.SessionCoachSnapshot
import com.cardenaspiero255.gamehubultra.domain.ThermalPredictionEngine
import com.cardenaspiero255.gamehubultra.domain.ThermalRisk
import com.cardenaspiero255.gamehubultra.platform.RefreshTelemetry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FramePacingIntelligenceEngineTest {
    private val policy = FramePacingPolicy()
    private val engine = FramePacingIntelligenceEngine(policy)

    @Test
    fun targetWithInsufficientSlowDataStaysRequested() {
        val assessment = engine.analyze(
            targetHz = 60,
            refreshSamples = emptyList(),
            frameTimingSamples = null,
            thermal = null,
            battery = null,
            supportedRefreshRatesHz = listOf(60, 90),
            interpolationState = InterpolationState(false, false, false)
        )

        assertEquals(RefreshStability.INSUFFICIENT_DATA, assessment.stability)
        assertEquals(RefreshVerification.REQUESTED, assessment.verification)
        assertNull(assessment.recommendedRefreshHz)
    }

    @Test
    fun observedSlowDataWithoutTargetIsObservedEvenWhenInsufficient() {
        val assessment = engine.analyze(
            targetHz = null,
            refreshSamples = slowSamples(90f, 90f),
            frameTimingSamples = null,
            thermal = null,
            battery = null,
            supportedRefreshRatesHz = listOf(60, 90),
            interpolationState = InterpolationState(false, false, false)
        )

        assertEquals(RefreshVerification.OBSERVED, assessment.verification)
    }

    @Test
    fun missingTargetAndSlowDataIsUnverifiable() {
        val assessment = engine.analyze(
            targetHz = null,
            refreshSamples = emptyList(),
            frameTimingSamples = null,
            thermal = null,
            battery = null,
            supportedRefreshRatesHz = emptyList(),
            interpolationState = InterpolationState(false, false, false)
        )

        assertEquals(RefreshVerification.UNVERIFIABLE, assessment.verification)
    }

    @Test
    fun timestampZeroIsAccepted() {
        val assessment = engine.analyze(
            targetHz = 60,
            refreshSamples = slowSamples(60f, 60f, 60f, 60f, 60f),
            frameTimingSamples = null,
            thermal = null,
            battery = null,
            supportedRefreshRatesHz = listOf(60, 90),
            interpolationState = InterpolationState(false, false, false)
        )

        assertEquals(5, assessment.sampleCountSlow)
        assertEquals(RefreshStability.STABLE, assessment.stability)
    }

    @Test
    fun stableRefreshVerifiesRequestedTarget() {
        val assessment = engine.analyze(
            targetHz = 60,
            refreshSamples = slowSamples(60f, 60f, 60f, 60f, 60f),
            frameTimingSamples = null,
            thermal = null,
            battery = null,
            supportedRefreshRatesHz = listOf(60, 72, 90, 120, 144, 165),
            interpolationState = InterpolationState(false, false, false)
        )

        assertEquals(RefreshStability.STABLE, assessment.stability)
        assertEquals(RefreshVerification.VERIFIED, assessment.verification)
        assertEquals(60, assessment.recommendedRefreshHz)
    }

    @Test
    fun minorVarianceNeedsEnoughEvidenceWithoutBecomingUnstable() {
        val assessment = engine.analyze(
            targetHz = 60,
            refreshSamples = slowSamples(60f, 60f, 68f, 68f, 68f),
            frameTimingSamples = null,
            thermal = null,
            battery = null,
            supportedRefreshRatesHz = listOf(60, 90),
            interpolationState = InterpolationState(false, false, false)
        )

        assertEquals(RefreshStability.MINOR_VARIANCE, assessment.stability)
    }

    @Test
    fun strongOscillationIsUnstable() {
        val assessment = engine.analyze(
            targetHz = 60,
            refreshSamples = slowSamples(60f, 90f, 60f, 90f, 60f),
            frameTimingSamples = null,
            thermal = null,
            battery = null,
            supportedRefreshRatesHz = listOf(60, 90),
            interpolationState = InterpolationState(false, false, false)
        )

        assertEquals(RefreshStability.UNSTABLE, assessment.stability)
        assertEquals(60, assessment.recommendedRefreshHz)
    }

    @Test
    fun sustainedDropIsDegradingBeforeItIsCalledUnstable() {
        val assessment = engine.analyze(
            targetHz = 90,
            refreshSamples = slowSamples(90f, 85f, 80f, 70f, 60f, 50f),
            frameTimingSamples = null,
            thermal = null,
            battery = null,
            supportedRefreshRatesHz = listOf(50, 60, 72, 90),
            interpolationState = InterpolationState(false, false, false)
        )

        assertEquals(RefreshStability.DEGRADING, assessment.stability)
        assertTrue(assessment.recommendedRefreshHz in listOf(50, 60, 72, 90))
    }

    @Test
    fun recoveryUsesNonOverlappingHeadAndTailSegments() {
        val assessment = engine.analyze(
            targetHz = 90,
            refreshSamples = slowSamples(50f, 50f, 50f, 90f, 90f, 90f),
            frameTimingSamples = null,
            thermal = null,
            battery = null,
            supportedRefreshRatesHz = listOf(50, 60, 90),
            interpolationState = InterpolationState(false, false, false)
        )

        assertEquals(RefreshStability.RECOVERING, assessment.stability)
    }

    @Test
    fun oldFastSamplesOutsideWindowDoNotMakePacingAvailable() {
        val fast = (0 until 30).map { index ->
            FrameTimingSample(index * 50L, 16f)
        } + FrameTimingSample(10_000L, 16f)

        val assessment = engine.analyze(
            targetHz = 60,
            refreshSamples = slowSamples(60f, 60f, 60f, 60f, 60f),
            frameTimingSamples = fast,
            thermal = null,
            battery = null,
            supportedRefreshRatesHz = listOf(60, 90),
            interpolationState = InterpolationState(false, false, false)
        )

        assertEquals(FrameDataAvailability.PACING_UNAVAILABLE, assessment.pacingAvailability)
        assertEquals(1, assessment.sampleCountFast)
    }

    @Test
    fun fastPathRemainsAvailableWhenSlowPathIsInsufficient() {
        val fast = (0..25).map { index ->
            FrameTimingSample(index * 50L, 16f)
        }

        val assessment = engine.analyze(
            targetHz = 60,
            refreshSamples = emptyList(),
            frameTimingSamples = fast,
            thermal = null,
            battery = null,
            supportedRefreshRatesHz = listOf(60, 90),
            interpolationState = InterpolationState(false, false, false)
        )

        assertEquals(FrameDataAvailability.AVAILABLE, assessment.pacingAvailability)
        assertNotNull(assessment.jitterMs)
        assertTrue(assessment.confidenceFast > 0f)
        assertTrue(assessment.confidenceCombined > 0f)
        assertEquals(RefreshStability.INSUFFICIENT_DATA, assessment.stability)
    }

    @Test
    fun interpolationIsIndependentFromSlowPathAndFailClosed() {
        val verified = engine.analyze(
            targetHz = 60,
            refreshSamples = emptyList(),
            frameTimingSamples = null,
            thermal = null,
            battery = null,
            supportedRefreshRatesHz = listOf(60),
            interpolationState = InterpolationState(true, true, true)
        )
        val notObserved = engine.analyze(
            targetHz = 60,
            refreshSamples = slowSamples(60f, 60f, 60f, 60f, 60f),
            frameTimingSamples = null,
            thermal = null,
            battery = null,
            supportedRefreshRatesHz = listOf(60),
            interpolationState = InterpolationState(true, true, false)
        )

        assertTrue(verified.interpolationVerified)
        assertFalse(notObserved.interpolationVerified)
    }

    @Test
    fun recommendationAlwaysUsesARealSupportedRateIncludingNonRoundModes() {
        val supported = listOf(60, 72, 90, 120, 144, 165)
        val assessment = engine.analyze(
            targetHz = 144,
            refreshSamples = slowSamples(144f, 144f, 144f, 144f, 144f),
            frameTimingSamples = null,
            thermal = null,
            battery = null,
            supportedRefreshRatesHz = supported,
            interpolationState = InterpolationState(false, false, false)
        )

        assertEquals(144, assessment.recommendedRefreshHz)
        assertTrue(assessment.recommendedRefreshHz in supported)
    }

    @Test
    fun safeFallbackUsesMinimumSupportedRateInsteadOfMaximum() {
        val supported = listOf(60, 90, 120)
        val assessment = engine.analyze(
            targetHz = 30,
            refreshSamples = slowSamples(30f, 30f, 30f, 30f, 30f),
            frameTimingSamples = null,
            thermal = null,
            battery = null,
            supportedRefreshRatesHz = supported,
            interpolationState = InterpolationState(false, false, false)
        )

        assertEquals(60, assessment.recommendedRefreshHz)
    }

    @Test
    fun thermalPreventiveSignalConstrainsRefreshAndHighRiskUsesMinimum() {
        val snapshots = List(10) { index ->
            snapshot(index * 15_000L, 90f)
        }
        val base = ThermalPredictionEngine().predict(snapshots)
        val moderate = base.copy(
            risk = ThermalRisk.MODERATE,
            allowPreventiveSignal = true
        )
        val high = base.copy(
            risk = ThermalRisk.HIGH,
            allowPreventiveSignal = true
        )
        val supported = listOf(60, 90, 120)

        val withoutThermal = engine.analyze(
            120,
            slowSamples(120f, 120f, 120f, 120f, 120f),
            null,
            null,
            null,
            supported,
            InterpolationState(false, false, false)
        )
        val withModerate = engine.analyze(
            120,
            slowSamples(120f, 120f, 120f, 120f, 120f),
            null,
            moderate,
            null,
            supported,
            InterpolationState(false, false, false)
        )
        val withHigh = engine.analyze(
            120,
            slowSamples(120f, 120f, 120f, 120f, 120f),
            null,
            high,
            null,
            supported,
            InterpolationState(false, false, false)
        )

        assertEquals(120, withoutThermal.recommendedRefreshHz)
        assertEquals(90, withModerate.recommendedRefreshHz)
        assertEquals(60, withHigh.recommendedRefreshHz)
    }

    @Test
    fun thermalRiskCannotBypassCar48PreventiveGate() {
        val snapshots = List(10) { index ->
            snapshot(index * 15_000L, 90f)
        }
        val noSignal = ThermalPredictionEngine().predict(snapshots).copy(
            risk = ThermalRisk.HIGH,
            allowPreventiveSignal = false
        )

        val assessment = engine.analyze(
            targetHz = 120,
            refreshSamples = slowSamples(120f, 120f, 120f, 120f, 120f),
            frameTimingSamples = null,
            thermal = noSignal,
            battery = null,
            supportedRefreshRatesHz = listOf(60, 90, 120),
            interpolationState = InterpolationState(false, false, false)
        )

        assertEquals(120, assessment.recommendedRefreshHz)
    }

    @Test
    fun batteryConstraintUsesCar49AssessmentWithoutInventingThermalPressure() {
        val snapshots = List(10) { index ->
            snapshot(index * 15_000L, 90f, batteryPercent = 12)
        }
        val battery = BatteryAwareGamingEngine().assess(snapshots)
        assertTrue(battery.preventAggressiveProfiles)

        val assessment = engine.analyze(
            targetHz = 90,
            refreshSamples = slowSamples(90f, 90f, 90f, 90f, 90f),
            frameTimingSamples = null,
            thermal = null,
            battery = battery,
            supportedRefreshRatesHz = listOf(60, 90, 120),
            interpolationState = InterpolationState(false, false, false)
        )

        assertEquals(60, assessment.recommendedRefreshHz)
    }

    @Test
    fun strongUnstableEvidenceCanHaveHighConfidenceAndNotify() {
        val samples = List(8) { index ->
            RefreshSample(
                timestampMs = index * policy.sampleIntervalSlowMs,
                refreshHz = if (index % 2 == 0) 60f else 90f
            )
        }

        val assessment = engine.analyze(
            targetHz = 60,
            refreshSamples = samples,
            frameTimingSamples = null,
            thermal = null,
            battery = null,
            supportedRefreshRatesHz = listOf(60, 90),
            interpolationState = InterpolationState(false, false, false)
        )
        val signal = engine.toAdaptiveSignal(assessment)

        assertEquals(RefreshStability.UNSTABLE, assessment.stability)
        assertTrue(assessment.confidenceSlow >= policy.minConfidence)
        assertTrue(signal.confidence >= policy.minConfidence)
        assertTrue(signal.shouldNotify)
    }

    @Test
    fun notAppliedUsesTheSameCombinedConfidenceExposedByAdaptiveSignal() {
        val assessment = engine.analyze(
            targetHz = 120,
            refreshSamples = List(10) { index ->
                RefreshSample(index * policy.sampleIntervalSlowMs, 90f)
            },
            frameTimingSamples = null,
            thermal = null,
            battery = null,
            supportedRefreshRatesHz = listOf(60, 90, 120),
            interpolationState = InterpolationState(false, false, false)
        )
        val signal = engine.toAdaptiveSignal(assessment)

        assertEquals(RefreshVerification.NOT_APPLIED, assessment.verification)
        assertEquals(assessment.confidenceCombined, signal.confidence)
        assertTrue(signal.shouldNotify)
    }

    @Test
    fun adapterUsesExplicitTimestampAndSharedPolicyBounds() {
        val customPolicy = FramePacingPolicy(
            refreshMinHz = 50f,
            refreshMaxHz = 200f,
            frameTimeMaxMs = 50f
        )
        val adapter = FramePacingTelemetryAdapter(customPolicy)
        val telemetry = RefreshTelemetry(
            supportedRefreshRatesHz = setOf(60, 72, 90, 120, 144, 165),
            currentRefreshRateHz = 144f
        )

        val samples = adapter.fromRefreshTelemetry(telemetry, 1234L)

        assertEquals(listOf(RefreshSample(1234L, 144f)), samples)
        assertEquals(listOf(60, 72, 90, 120, 144, 165), adapter.getSupportedFromTelemetry(telemetry))
    }

    @Test
    fun adapterRejectsNegativeTimestamp() {
        val telemetry = RefreshTelemetry(
            supportedRefreshRatesHz = setOf(60),
            currentRefreshRateHz = 60f
        )

        assertFailsWith<IllegalArgumentException> {
            FramePacingTelemetryAdapter().fromRefreshTelemetry(telemetry, -1L)
        }
    }

    @Test
    fun adapterRejectsMismatchedFrameTimingLists() {
        assertFailsWith<IllegalArgumentException> {
            FramePacingTelemetryAdapter().fromFrameTimings(
                frameTimesMs = listOf(16f),
                timestamps = listOf(0L, 1L)
            )
        }
    }

    @Test
    fun invalidFrameTimingsAreFilteredInsteadOfFabricated() {
        val samples = FramePacingTelemetryAdapter().fromFrameTimings(
            frameTimesMs = listOf(
                Float.NaN,
                Float.POSITIVE_INFINITY,
                -5f,
                0f,
                16f,
                policy.frameTimeMaxMs
            ),
            timestamps = listOf(0L, 10L, 20L, 30L, 40L, 50L)
        )

        assertEquals(listOf(FrameTimingSample(40L, 16f)), samples)
    }

    @Test
    fun policyRejectsImpossibleTrendWindowAndInvalidConfidenceWeights() {
        assertFailsWith<IllegalArgumentException> {
            FramePacingPolicy(
                minSamplesForTrend = 10,
                sampleIntervalSlowMs = 15_000L,
                windowSlowMs = 120_000L
            )
        }
        assertFailsWith<IllegalArgumentException> {
            FramePacingPolicy(confidenceDegradingBase = -0.1f)
        }
        assertFailsWith<IllegalArgumentException> {
            FramePacingPolicy(
                confidenceCombinedSlowWeight = 0.9f,
                confidenceCombinedFastWeight = 0.3f
            )
        }
    }

    private fun slowSamples(vararg values: Float): List<RefreshSample> =
        values.mapIndexed { index, value ->
            RefreshSample(
                timestampMs = index * policy.sampleIntervalSlowMs,
                refreshHz = value
            )
        }

    private fun snapshot(
        timestampMs: Long,
        refreshHz: Float?,
        batteryPercent: Int? = 50
    ): SessionCoachSnapshot =
        SessionCoachSnapshot(
            timestampMillis = timestampMs,
            batteryPercent = batteryPercent,
            thermalStatus = 2,
            thermalHeadroom = 0.5f,
            refreshRateHz = refreshHz,
            latencyMs = 16L,
            memoryUsedPercent = 50,
            batteryCharging = false,
            powerSaveMode = false
        )
}