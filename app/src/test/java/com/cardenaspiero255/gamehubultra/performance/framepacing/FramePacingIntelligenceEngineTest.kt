package com.cardenaspiero255.gamehubultra.performance.framepacing

import com.cardenaspiero255.gamehubultra.domain.BatteryGamingAssessment
import com.cardenaspiero255.gamehubultra.domain.BatteryGamingRecommendation
import com.cardenaspiero255.gamehubultra.domain.SessionCoachSnapshot
import com.cardenaspiero255.gamehubultra.domain.ThermalEvidence
import com.cardenaspiero255.gamehubultra.domain.ThermalPrediction
import com.cardenaspiero255.gamehubultra.domain.ThermalRisk
import com.cardenaspiero255.gamehubultra.domain.ThermalSignalMode
import com.cardenaspiero255.gamehubultra.domain.ThermalTrend
import com.cardenaspiero255.gamehubultra.platform.RefreshTelemetry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FramePacingIntelligenceEngineTest {
    private val engine = FramePacingIntelligenceEngine()

    @Test
    fun stableSlowPathVerifiesTargetWithoutInventingPacing() {
        val assessment = engine.analyze(
            targetHz = 120,
            refreshSamples = slowSamples(120f, 120f, 119.5f, 120.5f, 120f, 120f),
            frameTimingSamples = null,
            thermal = null,
            battery = null,
            supportedRefreshRatesHz = listOf(60, 90, 120, 144),
            interpolationState = InterpolationState(false, false, false)
        )

        assertEquals(RefreshStability.STABLE, assessment.stability)
        assertEquals(RefreshVerification.VERIFIED, assessment.verification)
        assertEquals(FrameDataAvailability.PACING_UNAVAILABLE, assessment.pacingAvailability)
        assertNull(assessment.jitterMs)
        assertEquals(120, assessment.recommendedRefreshHz)
        assertTrue(assessment.confidenceSlow >= 0.5f)
    }

    @Test
    fun insufficientSlowPathDistinguishesRequestedObservedAndUnverifiable() {
        val withTarget = engine.analyze(
            targetHz = 120,
            refreshSamples = listOf(RefreshSample(0L, 60f)),
            frameTimingSamples = null,
            thermal = null,
            battery = null,
            supportedRefreshRatesHz = listOf(60, 120),
            interpolationState = InterpolationState(false, false, false)
        )
        val observedOnly = engine.analyze(
            targetHz = null,
            refreshSamples = listOf(RefreshSample(0L, 90f)),
            frameTimingSamples = null,
            thermal = null,
            battery = null,
            supportedRefreshRatesHz = listOf(90),
            interpolationState = InterpolationState(false, false, false)
        )
        val none = engine.analyze(
            targetHz = null,
            refreshSamples = emptyList(),
            frameTimingSamples = null,
            thermal = null,
            battery = null,
            supportedRefreshRatesHz = emptyList(),
            interpolationState = InterpolationState(false, false, false)
        )

        assertEquals(RefreshVerification.REQUESTED, withTarget.verification)
        assertEquals(RefreshVerification.OBSERVED, observedOnly.verification)
        assertEquals(RefreshVerification.UNVERIFIABLE, none.verification)
    }

    @Test
    fun degradationIsReachableWithRealFifteenSecondCadenceAndTwoMinuteWindow() {
        val assessment = engine.analyze(
            targetHz = 120,
            refreshSamples = slowSamples(120f, 118f, 114f, 108f, 101f, 95f, 90f, 86f, 84f),
            frameTimingSamples = null,
            thermal = null,
            battery = null,
            supportedRefreshRatesHz = listOf(60, 90, 120),
            interpolationState = InterpolationState(false, false, false)
        )

        assertEquals(RefreshStability.DEGRADING, assessment.stability)
        assertTrue(assessment.recommendedRefreshHz in listOf(60, 90, 120))
    }

    @Test
    fun recoveryComparesStableTailAgainstEarlierLowerPeriod() {
        val assessment = engine.analyze(
            targetHz = 120,
            refreshSamples = slowSamples(60f, 60f, 60f, 72f, 90f, 120f, 120f, 120f, 120f),
            frameTimingSamples = null,
            thermal = null,
            battery = null,
            supportedRefreshRatesHz = listOf(60, 90, 120),
            interpolationState = InterpolationState(false, false, false)
        )

        assertEquals(RefreshStability.RECOVERING, assessment.stability)
    }

    @Test
    fun alternatingRefreshIsUnstableNotDegrading() {
        val assessment = engine.analyze(
            targetHz = 120,
            refreshSamples = slowSamples(120f, 60f, 120f, 60f, 120f, 60f, 120f, 60f, 120f),
            frameTimingSamples = null,
            thermal = null,
            battery = null,
            supportedRefreshRatesHz = listOf(60, 90, 120),
            interpolationState = InterpolationState(false, false, false)
        )

        assertEquals(RefreshStability.UNSTABLE, assessment.stability)
        assertEquals(60, assessment.recommendedRefreshHz)
    }

    @Test
    fun fastWindowUsesOnlyRecentValidFrameTimings() {
        val refresh = slowSamples(120f, 120f, 120f, 120f, 120f, 120f)
        val old = (0 until 30).map { FrameTimingSample(it * 50L, 8.3f) }
        val recentBase = 100_000L
        val recent = (0 until 20).map { FrameTimingSample(recentBase + it * 50L, 8.3f) }

        val staleAssessment = engine.analyze(
            120,
            refresh,
            old,
            null,
            null,
            listOf(120),
            InterpolationState(false, false, false)
        )
        val recentAssessment = engine.analyze(
            120,
            refresh,
            recent,
            null,
            null,
            listOf(120),
            InterpolationState(false, false, false)
        )

        assertEquals(FrameDataAvailability.AVAILABLE, staleAssessment.pacingAvailability)
        assertEquals(FrameDataAvailability.AVAILABLE, recentAssessment.pacingAvailability)
        assertEquals(20, recentAssessment.sampleCountFast)
    }

    @Test
    fun invalidFrameTimingsAreDiscardedBeforeAvailabilityAndJitter() {
        val timings = listOf(
            FrameTimingSample(-1L, 8.3f),
            FrameTimingSample(100L, Float.NaN),
            FrameTimingSample(200L, Float.POSITIVE_INFINITY),
            FrameTimingSample(300L, 0f),
            FrameTimingSample(400L, -1f)
        )

        val assessment = engine.analyze(
            120,
            slowSamples(120f, 120f, 120f, 120f, 120f, 120f),
            timings,
            null,
            null,
            listOf(120),
            InterpolationState(false, false, false)
        )

        assertEquals(FrameDataAvailability.PACING_UNAVAILABLE, assessment.pacingAvailability)
        assertEquals(0, assessment.sampleCountFast)
        assertNull(assessment.jitterMs)
    }

    @Test
    fun thermalAndBatteryTypesFromCar48AndCar49AreConsumedDirectly() {
        val thermal = thermalPrediction(
            risk = ThermalRisk.HIGH,
            trend = ThermalTrend.RISING,
            allowPreventiveSignal = true
        )
        val battery = batteryAssessment(preventAggressiveProfiles = true)

        val assessment = engine.analyze(
            144,
            slowSamples(144f, 144f, 144f, 144f, 144f, 144f),
            null,
            thermal,
            battery,
            listOf(72, 90, 120, 144, 165),
            InterpolationState(false, false, false)
        )

        assertEquals(72, assessment.recommendedRefreshHz)
        assertTrue(assessment.evidence.any { it.contains("térmic", ignoreCase = true) })
        assertTrue(assessment.evidence.any { it.contains("bater", ignoreCase = true) })
    }

    @Test
    fun allRealSupportedRefreshModesRemainEligible() {
        val assessment = engine.analyze(
            144,
            slowSamples(144f, 144f, 144f, 144f, 144f, 144f),
            null,
            null,
            null,
            listOf(72, 90, 120, 144, 165),
            InterpolationState(false, false, false)
        )

        assertEquals(144, assessment.recommendedRefreshHz)
    }

    @Test
    fun interpolationRemainsFailClosedWithoutObservedVendorState() {
        val notObserved = engine.analyze(
            120,
            slowSamples(120f, 120f, 120f, 120f, 120f, 120f),
            null,
            null,
            null,
            listOf(120),
            InterpolationState(requested = true, capabilityVerified = true, observedActive = false)
        )
        val observed = engine.analyze(
            120,
            slowSamples(120f, 120f, 120f, 120f, 120f, 120f),
            null,
            null,
            null,
            listOf(120),
            InterpolationState(requested = true, capabilityVerified = true, observedActive = true)
        )

        assertFalse(notObserved.interpolationVerified)
        assertTrue(observed.interpolationVerified)
    }

    @Test
    fun adapterUsesExistingNullableTelemetryWithoutInventingSamples() {
        val nullSnapshot = snapshot(15_000L, null)
        val validSnapshot = snapshot(30_000L, 90f)
        val nullTelemetry = RefreshTelemetry(setOf(60, 90, 120), null)
        val validTelemetry = RefreshTelemetry(setOf(60, 90, 120), 120f)

        assertNull(FramePacingTelemetryAdapter.fromSessionSnapshot(nullSnapshot))
        assertEquals(
            RefreshSample(30_000L, 90f),
            FramePacingTelemetryAdapter.fromSessionSnapshot(validSnapshot)
        )
        assertNull(FramePacingTelemetryAdapter.fromRefreshTelemetry(nullTelemetry, 45_000L))
        assertEquals(
            RefreshSample(45_000L, 120f),
            FramePacingTelemetryAdapter.fromRefreshTelemetry(validTelemetry, 45_000L)
        )
        assertEquals(
            listOf(60, 90, 120),
            FramePacingTelemetryAdapter.supportedRefreshRates(validTelemetry)
        )
    }

    @Test
    fun adaptiveSignalRequiresMinimumConfidenceBeforeNotification() {
        val low = FramePacingAssessment(
            targetHz = 120,
            observedRefreshHz = 60f,
            ewmaRefreshHz = 60f,
            pacingAvailability = FrameDataAvailability.PACING_UNAVAILABLE,
            stability = RefreshStability.UNSTABLE,
            varianceHz = 900f,
            stdDevHz = 30f,
            jitterMs = null,
            sampleCountSlow = 5,
            sampleCountFast = 0,
            confidenceSlow = 0.2f,
            confidenceCombined = 0.2f,
            evidence = emptyList(),
            explanation = "",
            recommendedRefreshHz = 60,
            verification = RefreshVerification.NOT_APPLIED,
            interpolationVerified = false
        )

        val signal = engine.toAdaptiveSignal(low)

        assertFalse(signal.shouldNotify)
    }


    @Test
    fun integrationConsumesExistingSessionAndRefreshTelemetry() {
        val integration = FramePacingIntegration(engine)
        val snapshots = listOf(
            snapshot(0L, 120f),
            snapshot(15_000L, 120f),
            snapshot(30_000L, 120f),
            snapshot(45_000L, 120f),
            snapshot(60_000L, 120f),
            snapshot(75_000L, 120f)
        )
        val telemetry = RefreshTelemetry(
            supportedRefreshRatesHz = setOf(60, 90, 120),
            currentRefreshRateHz = 120f
        )

        val assessment = integration.assess(
            targetHz = 120,
            sessionSnapshots = snapshots,
            frameTimingSamples = null,
            thermal = null,
            battery = null,
            refreshTelemetry = telemetry,
            interpolationState = InterpolationState(false, false, false)
        )

        assertEquals(RefreshVerification.VERIFIED, assessment.verification)
        assertEquals(120, assessment.recommendedRefreshHz)
        assertFalse(integration.shouldNotifyAdaptiveOptimizer(integration.adaptiveSignal(assessment)))
    }

    @Test
    fun policyRejectsBrokenHysteresis() {
        var failed = false
        try {
            FramePacingPolicy(
                targetObservedToleranceHz = 10f,
                notAppliedThresholdHz = 5f
            )
        } catch (_: IllegalArgumentException) {
            failed = true
        }
        assertTrue(failed)
    }

    private fun slowSamples(vararg hz: Float): List<RefreshSample> =
        hz.mapIndexed { index, value ->
            RefreshSample(timestampMs = index * 15_000L, refreshHz = value)
        }

    private fun snapshot(timestamp: Long, refresh: Float?): SessionCoachSnapshot =
        SessionCoachSnapshot(
            timestampMillis = timestamp,
            batteryPercent = 70,
            thermalStatus = 1,
            thermalHeadroom = 0.4f,
            refreshRateHz = refresh,
            latencyMs = 30L,
            memoryUsedPercent = 50,
            batteryCharging = false,
            powerSaveMode = false
        )

    private fun thermalPrediction(
        risk: ThermalRisk,
        trend: ThermalTrend,
        allowPreventiveSignal: Boolean
    ): ThermalPrediction =
        ThermalPrediction(
            trend = trend,
            risk = risk,
            confidence = 0.9f,
            signalMode = ThermalSignalMode.HEADROOM_AND_STATUS,
            slopePerMinute = 0.15f,
            accelerationPerMinuteSquared = 0f,
            latestMeasuredHeadroom = 0.8f,
            projectedHeadroom = 0.9f,
            allowPreventiveSignal = allowPreventiveSignal,
            recovering = false,
            evidence = emptyList<ThermalEvidence>(),
            reason = "test"
        )

    private fun batteryAssessment(
        preventAggressiveProfiles: Boolean
    ): BatteryGamingAssessment =
        BatteryGamingAssessment(
            currentPercent = 25,
            charging = false,
            powerSaveMode = false,
            observedDropPercent = 10,
            observedDurationMillis = 600_000L,
            drainPercentPerHour = 60f,
            recommendation = BatteryGamingRecommendation.CONSERVE,
            preventAggressiveProfiles = preventAggressiveProfiles,
            reason = "test"
        )
}
