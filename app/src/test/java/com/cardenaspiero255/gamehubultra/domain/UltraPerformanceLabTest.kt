package com.cardenaspiero255.gamehubultra.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UltraPerformanceLabTest {
    private val context = SyntheticBenchmarkContext(
        deviceModel = "test-android",
        sdkInt = 35,
        cpuCores = 8,
        appVersion = "1.2.0",
        scenario = SyntheticBenchmarkScenario.CPU_INTEGER_V1,
        workloadIterations = 25_000
    )
    private fun run(
        timings: List<Long>,
        profile: String = "BALANCED",
        benchmarkContext: SyntheticBenchmarkContext = context
    ) = SyntheticBenchmarkRun(
        benchmarkContext = benchmarkContext,
        profileName = profile,
        sampleNanos = timings,
        capturedAtMillis = 1000L
    )

    @Test fun repeatableScenarioUsesWarmupsButDoesNotCountThemAsMeasurements() {
        var ticks = 0L
        var workloads = 0
        val runner = SyntheticBenchmarkRunner(
            clockNanos = { ticks.also { ticks += 50_000L } },
            workload = { _ -> workloads++ }
        )
        val outcome = runner.measure(context, profileName = "BALANCED", warmups = 2, samples = 7)
        assertEquals(9, workloads)
        assertEquals(7, outcome.sampleNanos.size)
        assertTrue(outcome.sampleNanos.all { it == 50_000L })
        assertEquals(SyntheticSampleQuality.RELIABLE, UltraBenchmarkStatistics.quality(outcome))
        assertEquals(50_000L, UltraBenchmarkStatistics.medianNanos(outcome))
    }

    @Test fun invalidContextsAndWorkloadLimitsFailClosed() {
        val runner = SyntheticBenchmarkRunner(clockNanos = { 1L }, workload = {})
        assertFailsWith<IllegalArgumentException> {
            runner.measure(context.copy(workloadIterations = 0), "BALANCED")
        }
        assertFailsWith<IllegalArgumentException> {
            runner.measure(context.copy(workloadIterations = 1_000_001), "BALANCED")
        }
        assertFailsWith<IllegalArgumentException> {
            runner.measure(context.copy(deviceModel = ""), "BALANCED")
        }
        assertFailsWith<IllegalArgumentException> {
            runner.measure(context, "access_token=SECRET")
        }
        assertFailsWith<IllegalArgumentException> {
            runner.measure(context, "BALANCED", samples = 4)
        }
    }

    @Test fun statisticalQualityRejectsFewInvalidAndHighVarianceSamples() {
        assertEquals(SyntheticSampleQuality.INSUFFICIENT,
            UltraBenchmarkStatistics.quality(run(listOf(10, 10, 10, 10))))
        assertEquals(SyntheticSampleQuality.INVALID,
            UltraBenchmarkStatistics.quality(run(listOf(10, -1, 10, 10, 10))))
        assertEquals(SyntheticSampleQuality.UNSTABLE,
            UltraBenchmarkStatistics.quality(run(listOf(100, 100, 100, 100, 500))))
        assertEquals(SyntheticSampleQuality.RELIABLE,
            UltraBenchmarkStatistics.quality(run(listOf(99, 100, 100, 101, 100))))
    }

    @Test fun comparisonRejectsDeviceOSBuildScenarioAndWorkloadMismatches() {
        val before = run(listOf(300, 300, 300, 300, 300))
        val changes = listOf(
            context.copy(deviceModel = "other-device"),
            context.copy(sdkInt = 34),
            context.copy(appVersion = "1.3.0"),
            context.copy(workloadIterations = 50_000),
            context.copy(cpuCores = 4)
        )
        for (other in changes) {
            val result = UltraBenchmarkStatistics.compare(
                before, run(listOf(100, 100, 100, 100, 100),
                    profile = "X4", benchmarkContext = other)
            )
            assertEquals(SyntheticComparisonStatus.INCOMPARABLE, result.status)
            assertNull(result.relativePercent)
        }
    }

    @Test fun unreliableSamplesCannotClaimPerformanceGain() {
        val before = run(listOf(100, 100, 100, 100, 100))
        val after = run(listOf(50, 50, 50, 50), "X4")
        val result = UltraBenchmarkStatistics.compare(before, after)
        assertEquals(SyntheticComparisonStatus.INCONCLUSIVE, result.status)
        assertNull(result.relativePercent)
    }

    @Test fun disjointStableSamplesAllowSyntheticComparisonButNotFpsClaim() {
        val baseline = run(listOf(310, 300, 290, 305, 295))
        val measured = run(listOf(195, 200, 205, 202, 198), "X4")
        val result = UltraBenchmarkStatistics.compare(baseline, measured)
        assertEquals(SyntheticComparisonStatus.FASTER, result.status)
        assertNotNull(result.relativePercent)
        assertTrue(result.relativePercent > 20.0)
        assertTrue(result.disclaimer.contains("no son FPS"))
        assertFalse(result.disclaimer.contains("FPS reales garantizados"))
    }

    @Test fun overlappingTimesNeverProduceConfidentCausalClaim() {
        val result = UltraBenchmarkStatistics.compare(
            run(listOf(100, 110, 110, 120, 120)),
            run(listOf(105, 108, 112, 116, 122), "X4")
        )
        assertEquals(SyntheticComparisonStatus.INCONCLUSIVE, result.status)
        assertNull(result.relativePercent)
    }

    @Test fun benchmarkRetainsExactScenarioContextAndProfileLabelsOnly() {
        val outcome = run(listOf(300, 300, 300, 300, 300))
        assertEquals(context, outcome.benchmarkContext)
        assertEquals("BALANCED", outcome.profileName)
        assertEquals(SyntheticBenchmarkScenario.CPU_INTEGER_V1, outcome.benchmarkContext.scenario)
    }
}
