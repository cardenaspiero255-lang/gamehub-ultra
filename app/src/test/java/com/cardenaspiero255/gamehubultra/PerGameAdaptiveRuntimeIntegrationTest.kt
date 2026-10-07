package com.cardenaspiero255.gamehubultra

import com.cardenaspiero255.gamehubultra.data.SessionCoachStoredSession
import com.cardenaspiero255.gamehubultra.domain.PerformanceEvent
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.domain.PerGameAdaptiveOptimizer
import com.cardenaspiero255.gamehubultra.domain.SessionCoachSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PerGameAdaptiveRuntimeIntegrationTest {
    @Test
    fun completedSessionMemoryPressureAppliesBalancedAndRecordsReason() {
        val optimizer = PerGameAdaptiveOptimizer(
            confirmationsRequired = 1,
            cooldownMillis = 0
        )
        val completed = SessionCoachStoredSession(
            sessionId = "session-47",
            packageName = "game.a",
            startedAtMillis = 1_000L,
            endedAtMillis = 5_000L,
            samples = listOf(
                snapshot(1_000L, memory = 65),
                snapshot(3_000L, memory = 78),
                snapshot(5_000L, memory = 92)
            ),
            preSessionMessage = null,
            latestObservation = null
        )
        var applied: Pair<String, PerformanceProfile>? = null
        val events = mutableListOf<PerformanceEvent>()

        val decision = applyCompletedAdaptiveDecision(
            completed = completed,
            selectedPackage = "game.a",
            gameVersion = "1.2.3",
            activeProfile = PerformanceProfile.X4,
            optimizer = optimizer,
            nowMillis = 6_000L,
            applyProfile = { packageName, profile ->
                applied = packageName to profile
            },
            recordPerformanceEvent = events::add
        )

        assertNotNull(decision)
        assertTrue(decision.changed)
        assertEquals(PerformanceProfile.BALANCED, decision.profile)
        assertEquals("game.a" to PerformanceProfile.BALANCED, applied)
        assertEquals(1, events.size)
        assertEquals("session-47", events.single().sessionId)
        assertEquals(PerformanceProfile.BALANCED, events.single().profile)
        assertTrue(events.single().detail.contains("memoria"))
    }

    @Test
    fun completedSessionForAnotherGameNeverMutatesSelectedProfile() {
        val optimizer = PerGameAdaptiveOptimizer(confirmationsRequired = 1, cooldownMillis = 0)
        var applied = false
        val events = mutableListOf<PerformanceEvent>()

        val decision = applyCompletedAdaptiveDecision(
            completed = SessionCoachStoredSession(
                sessionId = "session-other",
                packageName = "game.other",
                startedAtMillis = 1L,
                endedAtMillis = 2L,
                samples = listOf(snapshot(2L, memory = 95)),
                preSessionMessage = null,
                latestObservation = null
            ),
            selectedPackage = "game.a",
            gameVersion = "1",
            activeProfile = PerformanceProfile.X4,
            optimizer = optimizer,
            nowMillis = 3L,
            applyProfile = { _, _ -> applied = true },
            recordPerformanceEvent = events::add
        )

        assertNull(decision)
        assertFalse(applied)
        assertTrue(events.isEmpty())
    }

    @Test
    fun completedSessionWithoutSamplesNeverMutatesProfile() {
        val optimizer = PerGameAdaptiveOptimizer(confirmationsRequired = 1, cooldownMillis = 0)
        var applied = false
        val events = mutableListOf<PerformanceEvent>()

        val decision = applyCompletedAdaptiveDecision(
            completed = SessionCoachStoredSession(
                sessionId = "session-empty",
                packageName = "game.a",
                startedAtMillis = 1L,
                endedAtMillis = 2L,
                samples = emptyList(),
                preSessionMessage = null,
                latestObservation = null
            ),
            selectedPackage = "game.a",
            gameVersion = "1",
            activeProfile = PerformanceProfile.X4,
            optimizer = optimizer,
            nowMillis = 3L,
            applyProfile = { _, _ -> applied = true },
            recordPerformanceEvent = events::add
        )

        assertNull(decision)
        assertFalse(applied)
        assertTrue(events.isEmpty())
    }

    private fun snapshot(
        timestamp: Long,
        memory: Int
    ) = SessionCoachSnapshot(
        timestampMillis = timestamp,
        batteryPercent = 80,
        thermalStatus = 1,
        thermalHeadroom = 0.2f,
        refreshRateHz = 120f,
        latencyMs = 30L,
        memoryUsedPercent = memory
    )
}
