package com.cardenaspiero255.gamehubultra.performance.framepacing

import com.cardenaspiero255.gamehubultra.domain.BatteryAwareGamingEngine
import com.cardenaspiero255.gamehubultra.domain.SessionCoachSnapshot
import com.cardenaspiero255.gamehubultra.domain.ThermalPredictionEngine
import com.cardenaspiero255.gamehubultra.platform.RefreshTelemetry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FramePacingIntelligenceEngineTest {
    private val policy = FramePacingPolicy(
        minSamples = 6,
        windowMs = 5_000L,
        noiseToleranceHz = 1.5f,
        varianceAllowedHz = 3.0f,
        maxTransitionsForUnstable = 3,
        targetObservedToleranceHz = 2.0f,
        minStabilityRatio = 0.8f,
        recoverySamples = 4,
        minConfidence = 0.6f
    )
    private val engine = FramePacingIntelligenceEngine(policy)

    @Test
    fun stableRefreshVerifiesRequestedTargetWithoutInventingGameFps() {
        val assessment = engine.analyze(
            targetHz = 120,
            samples = refreshSamples(120f, 120.5f, 119.5f, 120f, 120.4f, 119.8f),
            supportedRefreshRatesHz = setOf(60, 90, 120),
            observedGameFps = null,
            frameTimings = emptyList()
        )

        assertEquals(RefreshStability.STABLE, assessment.stability)
        assertEquals(RefreshVerification.VERIFIED, assessment.verification)
        assertEquals(FrameDataAvailability.FRAME_DATA_UNAVAILABLE, assessment.frameDataAvailability)
        assertEquals(null, assessment.observedGameFps)
        assertEquals(120, assessment.recommendedRefreshHz)
        assertFalse(assessment.interpolationVerified)
    }

    @Test
    fun oscillatingRefreshIsUnstableAndUsesOnlySupportedRecommendation() {
        val assessment = engine.analyze(
            targetHz = 120,
            samples = refreshSamples(120f, 60f, 120f, 60f, 120f, 60f, 120f),
            supportedRefreshRatesHz = setOf(60, 90, 120),
            observedGameFps = null,
            frameTimings = emptyList()
        )

        assertEquals(RefreshStability.UNSTABLE, assessment.stability)
        assertEquals(60, assessment.recommendedRefreshHz)
        assertTrue(assessment.recommendedRefreshHz in setOf(60, 90, 120))
        assertTrue(assessment.evidence.any { it.contains("oscil", ignoreCase = true) })
    }

    @Test
    fun onlyRecentWindowCountsTowardMinimumEvidence() {
        val samples = listOf(
            RefreshSample(0L, 120f),
            RefreshSample(100L, 120f),
            RefreshSample(200L, 120f),
            RefreshSample(8_000L, 120f),
            RefreshSample(8_500L, 120f),
            RefreshSample(9_000L, 120f)
        )
        val assessment = engine.analyze(
            targetHz = 120,
            samples = samples,
            supportedRefreshRatesHz = setOf(60, 120)
        )

        assertEquals(RefreshStability.INSUFFICIENT_DATA, assessment.stability)
        assertEquals(3, assessment.sampleCount)
    }

    @Test
    fun invalidRefreshSamplesAreDiscardedBeforeAnalysis() {
        val samples = refreshSamples(120f, Float.NaN, Float.POSITIVE_INFINITY, -1f, 120f, 120f)
        val assessment = engine.analyze(
            targetHz = 120,
            samples = samples,
            supportedRefreshRatesHz = setOf(60, 120)
        )

        assertEquals(RefreshStability.INSUFFICIENT_DATA, assessment.stability)
        assertEquals(3, assessment.sampleCount)
    }

    @Test
    fun observedRefreshWithoutTargetIsObservedNotUnverifiable() {
        val assessment = engine.analyze(
            targetHz = null,
            samples = refreshSamples(90f, 90f, 90f, 90f, 90f, 90f),
            supportedRefreshRatesHz = setOf(60, 90, 120)
        )

        assertEquals(RefreshVerification.OBSERVED, assessment.verification)
        assertEquals(90, assessment.recommendedRefreshHz)
    }

    @Test
    fun requestedTargetOutsideObservedToleranceIsNotApplied() {
        val assessment = engine.analyze(
            targetHz = 120,
            samples = refreshSamples(90f, 90f, 90f, 90f, 90f, 90f),
            supportedRefreshRatesHz = setOf(60, 90, 120)
        )

        assertEquals(RefreshVerification.NOT_APPLIED, assessment.verification)
    }

    @Test
    fun interpolationRemainsFailClosedWithoutObservedActiveState() {
        val assessment = engine.analyze(
            targetHz = 120,
            samples = refreshSamples(120f, 120f, 120f, 120f, 120f, 120f),
            supportedRefreshRatesHz = setOf(60, 120),
            interpolationRequested = true,
            interpolationCapabilityVerified = true,
            interpolationActiveObserved = null
        )

        assertFalse(assessment.interpolationVerified)
    }

    @Test
    fun interpolationCanBeVerifiedOnlyWhenRequestedCapableAndObservedActive() {
        val assessment = engine.analyze(
            targetHz = 120,
            samples = refreshSamples(120f, 120f, 120f, 120f, 120f, 120f),
            supportedRefreshRatesHz = setOf(60, 120),
            interpolationRequested = true,
            interpolationCapabilityVerified = true,
            interpolationActiveObserved = true
        )

        assertTrue(assessment.interpolationVerified)
    }

    @Test
    fun verifiedExternalFpsDoesNotGetDerivedFromRefresh() {
        val assessment = engine.analyze(
            targetHz = 120,
            samples = refreshSamples(120f, 120f, 120f, 120f, 120f, 120f),
            supportedRefreshRatesHz = setOf(60, 120),
            observedGameFps = 58f,
            frameTimings = emptyList()
        )

        assertEquals(58f, assessment.observedGameFps)
        assertEquals(FrameDataAvailability.PACING_UNAVAILABLE, assessment.frameDataAvailability)
        assertTrue(assessment.explanation.contains("Hz", ignoreCase = true))
    }

    @Test
    fun typedThermalAndBatteryAssessmentsAddCorrelationWithoutClaimingCausality() {
        val session = listOf(
            snapshot(0L, 120f, 70, 1, 0.50f),
            snapshot(60_000L, 120f, 65, 2, 0.60f),
            snapshot(120_000L, 90f, 60, 3, 0.76f),
            snapshot(180_000L, 90f, 55, 3, 0.80f),
            snapshot(240_000L, 60f, 30, 3, 0.84f),
            snapshot(300_000L, 60f, 25, 3, 0.86f)
        )
        val thermal = ThermalPredictionEngine().predict(session)
        val battery = BatteryAwareGamingEngine().assess(session)
        val assessment = engine.analyze(
            targetHz = 120,
            samples = FramePacingTelemetryAdapter.fromSessionSnapshots(session),
            supportedRefreshRatesHz = setOf(60, 90, 120),
            thermalPrediction = thermal,
            batteryAssessment = battery
        )

        assertTrue(assessment.evidence.any { it.contains("térmic", ignoreCase = true) })
        assertTrue(assessment.evidence.any { it.contains("bater", ignoreCase = true) })
        assertFalse(assessment.explanation.contains("causó", ignoreCase = true))
    }

    @Test
    fun telemetryAdapterReusesExistingRefreshTelemetryAndSessionSnapshot() {
        val telemetry = RefreshTelemetry(
            supportedRefreshRatesHz = setOf(60, 120),
            currentRefreshRateHz = 119.8f
        )
        val fromRuntime = FramePacingTelemetryAdapter.fromRefreshTelemetry(telemetry, 100L)
        val fromSession = FramePacingTelemetryAdapter.fromSessionSnapshots(
            listOf(snapshot(200L, 90f, 80, 1, 0.40f))
        )

        assertNotNull(fromRuntime)
        assertEquals(119.8f, fromRuntime.refreshHz)
        assertEquals(listOf(RefreshSample(200L, 90f)), fromSession)
    }

    @Test
    fun policyRejectsInvalidConfiguration() {
        var failed = false
        try {
            FramePacingPolicy(minSamples = 2)
        } catch (_: IllegalArgumentException) {
            failed = true
        }
        assertTrue(failed)
    }

    private fun refreshSamples(vararg values: Float): List<RefreshSample> =
        values.mapIndexed { index, value ->
            RefreshSample(timestampMs = index * 500L, refreshHz = value)
        }

    private fun snapshot(
        timestamp: Long,
        refresh: Float,
        battery: Int,
        thermalStatus: Int,
        headroom: Float
    ): SessionCoachSnapshot =
        SessionCoachSnapshot(
            timestampMillis = timestamp,
            batteryPercent = battery,
            thermalStatus = thermalStatus,
            thermalHeadroom = headroom,
            refreshRateHz = refresh,
            latencyMs = 30L,
            memoryUsedPercent = 50,
            batteryCharging = false,
            powerSaveMode = false
        )
}
