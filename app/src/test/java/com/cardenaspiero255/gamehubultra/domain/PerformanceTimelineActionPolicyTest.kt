package com.cardenaspiero255.gamehubultra.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PerformanceTimelineActionPolicyTest {
    @Test
    fun emptyTimelineDisablesShareWithVisibleReason() {
        val timeline = PerformanceTimeline(
            samples = emptyList(),
            profileEvents = emptyList(),
            thermalEvents = emptyList(),
            fpsAvailable = false
        )

        assertFalse(PerformanceTimelineActionPolicy.canShare(timeline))
        assertEquals("Sin datos", PerformanceTimelineActionPolicy.shareLabel(timeline))
        assertNotNull(PerformanceTimelineActionPolicy.disabledShareReason(timeline))
    }

    @Test
    fun timelineWithTelemetryEnablesShare() {
        val timeline = PerformanceTimeline(
            samples = listOf(
                PerformanceTimelineSample(
                    timestampMillis = 1L,
                    batteryPercent = 80,
                    thermalStatus = null,
                    thermalHeadroomPercent = null,
                    refreshRateHz = 120,
                    ramUsedPercent = 42
                )
            ),
            profileEvents = emptyList(),
            thermalEvents = emptyList(),
            fpsAvailable = false
        )

        assertTrue(PerformanceTimelineActionPolicy.canShare(timeline))
        assertEquals("Compartir", PerformanceTimelineActionPolicy.shareLabel(timeline))
        assertNull(PerformanceTimelineActionPolicy.disabledShareReason(timeline))
    }

    @Test
    fun timelineWithEventsEnablesShareEvenWithoutSamples() {
        val timeline = PerformanceTimeline(
            samples = emptyList(),
            profileEvents = listOf(
                PerformanceEvent(
                    timestampMillis = 1L,
                    type = PerformanceEventType.POLICY_CHANGED,
                    sessionId = "session",
                    profile = PerformanceProfile.BALANCED
                )
            ),
            thermalEvents = emptyList(),
            fpsAvailable = false
        )

        assertTrue(PerformanceTimelineActionPolicy.canShare(timeline))
        assertEquals("Compartir", PerformanceTimelineActionPolicy.shareLabel(timeline))
        assertNull(PerformanceTimelineActionPolicy.disabledShareReason(timeline))
    }
}
