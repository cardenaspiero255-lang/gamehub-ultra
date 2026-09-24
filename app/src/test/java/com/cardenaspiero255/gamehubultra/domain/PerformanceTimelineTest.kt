package com.cardenaspiero255.gamehubultra.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PerformanceTimelineTest {
    @Test
    fun sampleSanitizesAndRoundsRuntimeMetrics() {
        val sample = PerformanceTimelineBuilder.sample(
            timestampMillis = 1000L, batteryPercent = 140, thermalStatus = 3,
            thermalHeadroom = 0.456f, refreshRateHz = 119.6f, ramUsedPercent = 101
        )
        assertEquals(100, sample.batteryPercent)
        assertEquals(46, sample.thermalHeadroomPercent)
        assertEquals(120, sample.refreshRateHz)
        assertEquals(100, sample.ramUsedPercent)
    }

    @Test
    fun buildKeepsOnlyRelevantSessionEventsAndBoundsSamples() {
        val samples = (0L..30L).map { index ->
            PerformanceTimelineBuilder.sample(
                timestampMillis = index, batteryPercent = 80, thermalStatus = 0,
                thermalHeadroom = 0.2f, refreshRateHz = 120f, ramUsedPercent = 50
            )
        }
        val events = listOf(
            PerformanceEvent(2L, PerformanceEventType.POLICY_CHANGED, "s1", PerformanceProfile.X4),
            PerformanceEvent(3L, PerformanceEventType.THERMAL_CHANGED, "s1", detail = "3"),
            PerformanceEvent(4L, PerformanceEventType.POLICY_CHANGED, "other", PerformanceProfile.BALANCED)
        )
        val timeline = PerformanceTimelineBuilder.build(samples, events, activeSessionId = "s1")
        assertEquals(24, timeline.samples.size)
        assertEquals(1, timeline.profileEvents.size)
        assertEquals(1, timeline.thermalEvents.size)
        assertFalse(timeline.fpsAvailable)
        assertTrue(timeline.samples.first().timestampMillis > 0L)
    }

    @Test
    fun reportNeverContainsCredentialsOrTokens() {
        val sample = PerformanceTimelineBuilder.sample(
            timestampMillis = 1L, batteryPercent = 80, thermalStatus = 0,
            thermalHeadroom = 0.3f, refreshRateHz = 120f, ramUsedPercent = 40
        )
        val report = PerformanceTimelineReportFormatter.format(
            gamePackage = "com.example.game",
            timeline = PerformanceTimelineBuilder.build(listOf(sample), emptyList())
        )
        assertTrue(report.contains("com.example.game"))
        assertTrue(report.contains("FPS: no disponible"))
        assertFalse(report.contains("password", ignoreCase = true))
        assertFalse(report.contains("token", ignoreCase = true))
    }
}