package com.cardenaspiero255.gamehubultra.domain

import com.cardenaspiero255.gamehubultra.data.GameSessionRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionReplayTimelineTest {
    private fun completed(
        id: String = "session-a",
        startedAt: Long = 1_000L,
        endedAt: Long? = 11_000L
    ) = GameSessionRecord(
        id = id,
        packageName = "game.example",
        profileName = "BALANCED",
        startedAtMillis = startedAt,
        endedAtMillis = endedAt,
        startBatteryPercent = 80,
        endBatteryPercent = 72,
        endThermalStatus = 3
    )

    private fun event(
        timestamp: Long,
        type: PerformanceEventType,
        id: String = "session-a",
        profile: PerformanceProfile? = null,
        detail: String = ""
    ) = PerformanceEvent(
        timestampMillis = timestamp,
        type = type,
        sessionId = id,
        profile = profile,
        detail = detail
    )

    @Test fun replayIncludesOnlyRealSessionScopedMarkersAndKeepsOriginsDistinct() {
        val replay = assertNotNull(SessionReplayTimelineBuilder.build(
            completed(),
            listOf(
                event(6_000, PerformanceEventType.THERMAL_CHANGED, detail = "4"),
                event(3_000, PerformanceEventType.POLICY_CHANGED,
                    profile = PerformanceProfile.X4, detail = "manual_profile_selection"),
                event(4_000, PerformanceEventType.POLICY_CHANGED,
                    profile = PerformanceProfile.BALANCED, detail = "adaptive_thermal"),
                event(5_000, PerformanceEventType.POLICY_CHANGED, id = "other",
                    profile = PerformanceProfile.X4),
                event(12_000, PerformanceEventType.THERMAL_CHANGED, detail = "6"),
                event(800, PerformanceEventType.THERMAL_CHANGED, detail = "0")
            )
        ))
        assertEquals(
            listOf(
                SessionReplayMarkerKind.START,
                SessionReplayMarkerKind.MANUAL_PROFILE,
                SessionReplayMarkerKind.AUTOMATIC_PROFILE,
                SessionReplayMarkerKind.THERMAL,
                SessionReplayMarkerKind.END
            ),
            replay.markers.map { it.kind }
        )
        assertEquals(listOf(0L, 2_000L, 3_000L, 5_000L, 10_000L),
            replay.markers.map { it.elapsedMillis })
        assertEquals(80, replay.markers.first().batteryPercent)
        assertEquals(72, replay.markers.last().batteryPercent)
        assertEquals(3, replay.markers.last().thermalStatus)
        assertFalse(replay.hasRefreshHistory)
        assertFalse(replay.hasLatencyHistory)
        assertFalse(replay.truncated)
    }

    @Test fun activeOrContradictorySessionsCannotBePresentedAsCompletedReplay() {
        assertNull(SessionReplayTimelineBuilder.build(completed(endedAt = null), emptyList()))
        assertNull(SessionReplayTimelineBuilder.build(completed(endedAt = 999L), emptyList()))
        assertNull(SessionReplayTimelineBuilder.build(completed(id = ""), emptyList()))
    }

    @Test fun missingTelemetryNeverBecomesFabricatedRefreshFpsOrLatency() {
        val replay = assertNotNull(SessionReplayTimelineBuilder.build(
            completed(),
            emptyList()
        ))
        assertEquals(2, replay.markers.size)
        assertEquals(null, replay.markers.first().thermalStatus)
        assertEquals(null, replay.markers.last().refreshRateHz)
        assertEquals(null, replay.markers.last().latencyMillis)
        assertFalse(replay.hasRefreshHistory)
        assertFalse(replay.hasLatencyHistory)
    }

    @Test fun malformedThermalAndUnknownProfileOriginsStayUnverified() {
        val replay = assertNotNull(SessionReplayTimelineBuilder.build(
            completed(),
            listOf(
                event(2_000, PerformanceEventType.THERMAL_CHANGED,
                    detail = "access_token=SECRET"),
                event(3_000, PerformanceEventType.THERMAL_CHANGED, detail = "8"),
                event(4_000, PerformanceEventType.POLICY_CHANGED,
                    profile = PerformanceProfile.X4, detail = ""),
                event(5_000, PerformanceEventType.POLICY_CHANGED,
                    profile = null, detail = "manual_profile_selection")
            )
        ))
        assertEquals(
            listOf(SessionReplayMarkerKind.START, SessionReplayMarkerKind.UNKNOWN_PROFILE,
                SessionReplayMarkerKind.END), replay.markers.map { it.kind }
        )
        assertTrue(replay.markers.none { it.label().contains("SECRET") })
        assertTrue(replay.markers.none { it.label().contains("access_token") })
    }

    @Test fun equalTimestampMarkersPreserveInputOrderAndSessionBoundaries() {
        val replay = assertNotNull(SessionReplayTimelineBuilder.build(
            completed(),
            listOf(
                event(1_000, PerformanceEventType.POLICY_CHANGED,
                    profile = PerformanceProfile.X4, detail = "manual_profile_selection"),
                event(11_000, PerformanceEventType.THERMAL_CHANGED, detail = "2")
            )
        ))
        assertEquals(listOf(SessionReplayMarkerKind.START,
            SessionReplayMarkerKind.MANUAL_PROFILE, SessionReplayMarkerKind.THERMAL,
            SessionReplayMarkerKind.END), replay.markers.map { it.kind })
    }

    @Test fun boundedReplayRetainsLatestRecordedChangesWithHonestTruncation() {
        val events = (1..40).map { index ->
            event(1_000L + index * 100L, PerformanceEventType.THERMAL_CHANGED,
                detail = (index % 7).toString())
        }
        val replay = assertNotNull(SessionReplayTimelineBuilder.build(completed(), events))
        assertEquals(22, replay.markers.size) // 20 real events + start/end
        assertTrue(replay.truncated)
        assertEquals(40, replay.recordedEventCount)
        assertEquals(20, replay.shownEventCount)
        assertEquals(SessionReplayMarkerKind.START, replay.markers.first().kind)
        assertEquals(SessionReplayMarkerKind.END, replay.markers.last().kind)
    }

    @Test fun sessionIdMustMatchExactlyWithoutCrossSessionDataLeakage() {
        val replay = assertNotNull(SessionReplayTimelineBuilder.build(
            completed(id = "abc"),
            listOf(
                event(2_000, PerformanceEventType.THERMAL_CHANGED, id = "ABC", detail = "2"),
                event(3_000, PerformanceEventType.THERMAL_CHANGED, id = "abc ", detail = "3"),
                event(4_000, PerformanceEventType.THERMAL_CHANGED, id = "abc", detail = "5")
            )
        ))
        assertEquals(3, replay.markers.size)
        assertEquals(5, replay.markers[1].thermalStatus)
    }
}
