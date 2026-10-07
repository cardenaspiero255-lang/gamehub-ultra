package com.cardenaspiero255.gamehubultra

import com.cardenaspiero255.gamehubultra.data.SessionCoachStoredSession
import com.cardenaspiero255.gamehubultra.domain.PerformanceEvent
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.domain.AdaptiveGameKey
import com.cardenaspiero255.gamehubultra.domain.PerGameAdaptiveOptimizer
import com.cardenaspiero255.gamehubultra.domain.PerGameAdaptivePersistedState
import com.cardenaspiero255.gamehubultra.domain.PerGameAdaptiveStateStore
import com.cardenaspiero255.gamehubultra.domain.SessionCoachSnapshot
import kotlinx.coroutines.isActive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
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
            latestObservation = null,
            gameVersion = "1.2.3"
        )
        var applied: Pair<String, PerformanceProfile>? = null
        val events = mutableListOf<PerformanceEvent>()

        val decision = applyCompletedAdaptiveDecision(
            completed = completed,
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
    fun adaptiveStateIsKeyedByVersionCapturedWithCompletedSession() {
        val writtenKeys = mutableListOf<AdaptiveGameKey>()
        val store = object : PerGameAdaptiveStateStore {
            override fun read(key: AdaptiveGameKey): PerGameAdaptivePersistedState? = null
            override fun write(key: AdaptiveGameKey, state: PerGameAdaptivePersistedState) {
                writtenKeys += key
            }
        }
        val optimizer = PerGameAdaptiveOptimizer(
            confirmationsRequired = 1,
            cooldownMillis = 0,
            stateStore = store
        )

        applyCompletedAdaptiveDecision(
            completed = SessionCoachStoredSession(
                sessionId = "session-version",
                packageName = "game.a",
                startedAtMillis = 1L,
                endedAtMillis = 2L,
                samples = listOf(snapshot(2L, memory = 95)),
                preSessionMessage = null,
                latestObservation = null,
                gameVersion = "7.4.2"
            ),
            activeProfile = PerformanceProfile.X4,
            optimizer = optimizer,
            nowMillis = 3L,
            applyProfile = { _, _ -> },
            recordPerformanceEvent = {}
        )

        assertEquals("7.4.2", writtenKeys.single().version)
    }

    @Test
    fun completedSessionOptimizesTheGameThatProducedIt() {
        val optimizer = PerGameAdaptiveOptimizer(confirmationsRequired = 1, cooldownMillis = 0)
        var applied: Pair<String, PerformanceProfile>? = null
        val events = mutableListOf<PerformanceEvent>()

        val decision = applyCompletedAdaptiveDecision(
            completed = SessionCoachStoredSession(
                sessionId = "session-owning-game",
                packageName = "game.a",
                startedAtMillis = 1L,
                endedAtMillis = 2L,
                samples = listOf(snapshot(2L, memory = 95)),
                preSessionMessage = null,
                latestObservation = null,
                gameVersion = "3.0"
            ),
            activeProfile = PerformanceProfile.X4,
            optimizer = optimizer,
            nowMillis = 3L,
            applyProfile = { packageName, profile ->
                applied = packageName to profile
            },
            recordPerformanceEvent = events::add
        )

        assertNotNull(decision)
        assertTrue(decision.changed)
        assertEquals("game.a" to PerformanceProfile.BALANCED, applied)
        assertEquals("session-owning-game", events.single().sessionId)
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

    @Test
    fun processMarksEmptyCompletedSessionHandledWithoutPersisting() = kotlinx.coroutines.runBlocking {
        var handled = false
        var persisted = false
        val result = processCompletedAdaptiveSession(
            completed = SessionCoachStoredSession(
                sessionId = "session-empty-process",
                packageName = "game.a",
                startedAtMillis = 1L,
                endedAtMillis = 2L,
                samples = emptyList(),
                preSessionMessage = null,
                latestObservation = null,
                gameVersion = "1#1"
            ),
            optimizer = PerGameAdaptiveOptimizer(confirmationsRequired = 1, cooldownMillis = 0),
            nowMillis = 3L,
            wasSessionHandled = { handled },
            resolveActiveProfile = { PerformanceProfile.X4 },
            persistProfile = { _, _ -> persisted = true },
            markSessionHandled = { handled = true },
            recordPerformanceEvent = {}
        )

        assertNull(result)
        assertTrue(handled)
        assertFalse(persisted)
    }

    @Test
    fun processMarksMalformedPackageHandledWithoutPersisting() = kotlinx.coroutines.runBlocking {
        var handled = false
        var persisted = false
        val result = processCompletedAdaptiveSession(
            completed = SessionCoachStoredSession(
                sessionId = "session-bad-package",
                packageName = "   ",
                startedAtMillis = 1L,
                endedAtMillis = 2L,
                samples = listOf(snapshot(2L, memory = 95)),
                preSessionMessage = null,
                latestObservation = null,
                gameVersion = "1#1"
            ),
            optimizer = PerGameAdaptiveOptimizer(confirmationsRequired = 1, cooldownMillis = 0),
            nowMillis = 3L,
            wasSessionHandled = { handled },
            resolveActiveProfile = { PerformanceProfile.X4 },
            persistProfile = { _, _ -> persisted = true },
            markSessionHandled = { handled = true },
            recordPerformanceEvent = {}
        )

        assertNull(result)
        assertTrue(handled)
        assertFalse(persisted)
    }


    @Test
    fun eventPersistenceFailureRollsBackProfileAndAllowsRetry() = kotlinx.coroutines.runBlocking {
        val optimizer = PerGameAdaptiveOptimizer(
            confirmationsRequired = 1,
            cooldownMillis = 0
        )
        val completed = SessionCoachStoredSession(
            sessionId = "session-event-retry",
            packageName = "game.a",
            startedAtMillis = 1L,
            endedAtMillis = 2L,
            samples = listOf(snapshot(2L, memory = 95)),
            preSessionMessage = null,
            latestObservation = null,
            gameVersion = "1#1"
        )
        var handled = false
        var currentProfile = PerformanceProfile.X4
        var eventAttempts = 0

        assertFailsWith<IllegalStateException> {
            processCompletedAdaptiveSession(
                completed = completed,
                optimizer = optimizer,
                nowMillis = 3L,
                wasSessionHandled = { handled },
                resolveActiveProfile = { currentProfile },
                persistProfile = { _, profile -> currentProfile = profile },
                markSessionHandled = { handled = true },
                recordPerformanceEvent = {
                    eventAttempts++
                    error("simulated event persistence failure")
                }
            )
        }

        assertFalse(handled)
        assertEquals(PerformanceProfile.X4, currentProfile)
        assertEquals(1, eventAttempts)

        val retried = processCompletedAdaptiveSession(
            completed = completed,
            optimizer = optimizer,
            nowMillis = 4L,
            wasSessionHandled = { handled },
            resolveActiveProfile = { currentProfile },
            persistProfile = { _, profile -> currentProfile = profile },
            markSessionHandled = { handled = true },
            recordPerformanceEvent = { eventAttempts++ }
        )

        assertNotNull(retried)
        assertTrue(retried.changed)
        assertTrue(handled)
        assertEquals(PerformanceProfile.BALANCED, currentProfile)
        assertEquals(2, eventAttempts)
    }

    @Test
    fun failedProfilePersistenceDoesNotMarkSessionHandledAndCanRetry() = kotlinx.coroutines.runBlocking {
        val optimizer = PerGameAdaptiveOptimizer(
            confirmationsRequired = 1,
            cooldownMillis = 0
        )
        val completed = SessionCoachStoredSession(
            sessionId = "session-retry",
            packageName = "game.a",
            startedAtMillis = 1L,
            endedAtMillis = 2L,
            samples = listOf(snapshot(2L, memory = 95)),
            preSessionMessage = null,
            latestObservation = null,
            gameVersion = "9#42"
        )
        var handled = false
        var persistAttempts = 0

        assertFailsWith<IllegalStateException> {
            processCompletedAdaptiveSession(
                completed = completed,
                optimizer = optimizer,
                nowMillis = 3L,
                wasSessionHandled = { handled },
                resolveActiveProfile = { PerformanceProfile.X4 },
                persistProfile = { _, _ ->
                    persistAttempts++
                    error("simulated persistence failure")
                },
                markSessionHandled = { handled = true },
                recordPerformanceEvent = {}
            )
        }

        assertFalse(handled)
        assertEquals(1, persistAttempts)

        val retried = processCompletedAdaptiveSession(
            completed = completed,
            optimizer = optimizer,
            nowMillis = 4L,
            wasSessionHandled = { handled },
            resolveActiveProfile = { PerformanceProfile.X4 },
            persistProfile = { _, _ -> persistAttempts++ },
            markSessionHandled = { handled = true },
            recordPerformanceEvent = {}
        )

        assertNotNull(retried)
        assertTrue(retried.changed)
        assertTrue(handled)
        assertEquals(2, persistAttempts)
    }

    @Test
    fun cancellationAfterProfileWriteRollsBackInNonCancellableContext() = kotlinx.coroutines.runBlocking {
        val optimizer = PerGameAdaptiveOptimizer(confirmationsRequired = 1, cooldownMillis = 0)
        val completed = SessionCoachStoredSession(
            sessionId = "session-cancel",
            packageName = "game.a",
            startedAtMillis = 1L,
            endedAtMillis = 2L,
            samples = listOf(snapshot(2L, memory = 95)),
            preSessionMessage = null,
            latestObservation = null,
            gameVersion = "1#1"
        )
        var currentProfile = PerformanceProfile.X4
        var handled = false

        assertFailsWith<kotlinx.coroutines.CancellationException> {
            processCompletedAdaptiveSession(
                completed = completed,
                optimizer = optimizer,
                nowMillis = 3L,
                wasSessionHandled = { handled },
                resolveActiveProfile = { currentProfile },
                persistProfile = { _, profile ->
                    if (profile == PerformanceProfile.X4) {
                        assertTrue(kotlinx.coroutines.currentCoroutineContext().isActive)
                    }
                    currentProfile = profile
                },
                markSessionHandled = { handled = true },
                recordPerformanceEvent = {
                    throw kotlinx.coroutines.CancellationException("cancel after profile write")
                }
            )
        }

        assertEquals(PerformanceProfile.X4, currentProfile)
        assertFalse(handled)
    }

    @Test
    fun safeAdaptiveProcessingKeepsCleanupRunningButRethrowsCancellation() = kotlinx.coroutines.runBlocking {
        var reported: Throwable? = null

        val result = runAdaptiveSessionProcessing(
            process = { error("persistence failed") },
            onError = { reported = it }
        )

        assertNull(result)
        assertTrue(reported is IllegalStateException)

        assertFailsWith<kotlinx.coroutines.CancellationException> {
            runAdaptiveSessionProcessing(
                process = { throw kotlinx.coroutines.CancellationException("cancel") },
                onError = { error("cancellation must not be swallowed") }
            )
        }
        Unit
    }


    @Test
    fun pendingDecisionReplaysAfterOptimizerRecreationWithoutLosingReason() = kotlinx.coroutines.runBlocking {
        val persisted = mutableMapOf<AdaptiveGameKey, PerGameAdaptivePersistedState>()
        var pending: com.cardenaspiero255.gamehubultra.domain.PerGameAdaptivePendingDecision? = null
        val store = object : PerGameAdaptiveStateStore {
            override fun read(key: AdaptiveGameKey) = persisted[key]
            override fun write(key: AdaptiveGameKey, state: PerGameAdaptivePersistedState) {
                persisted[key] = state
            }
            override fun readPendingDecision(sessionId: String) =
                pending?.takeIf { it.sessionId == sessionId }
            override fun writePendingDecision(
                decision: com.cardenaspiero255.gamehubultra.domain.PerGameAdaptivePendingDecision
            ) {
                pending = decision
            }
            override fun clearPendingDecision(sessionId: String) {
                if (pending?.sessionId == sessionId) pending = null
            }
        }
        val completed = SessionCoachStoredSession(
            sessionId = "session-crash-safe",
            packageName = "game.a",
            startedAtMillis = 1L,
            endedAtMillis = 2L,
            samples = listOf(snapshot(2L, memory = 95)),
            preSessionMessage = null,
            latestObservation = null,
            gameVersion = "1#1"
        )
        var handled = false
        var currentProfile = PerformanceProfile.X4
        val events = mutableListOf<PerformanceEvent>()

        assertFailsWith<IllegalStateException> {
            processCompletedAdaptiveSession(
                completed = completed,
                optimizer = PerGameAdaptiveOptimizer(
                    confirmationsRequired = 1,
                    cooldownMillis = 0,
                    stateStore = store
                ),
                nowMillis = 3L,
                wasSessionHandled = { handled },
                resolveActiveProfile = { currentProfile },
                persistProfile = { _, profile -> currentProfile = profile },
                markSessionHandled = { handled = true },
                recordPerformanceEvent = { error("simulated event store outage") }
            )
        }

        assertFalse(handled)
        assertEquals(PerformanceProfile.X4, currentProfile)
        assertNotNull(pending)

        val replayed = processCompletedAdaptiveSession(
            completed = completed,
            optimizer = PerGameAdaptiveOptimizer(
                confirmationsRequired = 1,
                cooldownMillis = 0,
                stateStore = store
            ),
            nowMillis = 4L,
            wasSessionHandled = { handled },
            resolveActiveProfile = { currentProfile },
            persistProfile = { _, profile -> currentProfile = profile },
            markSessionHandled = { handled = true },
            recordPerformanceEvent = events::add
        )

        assertNotNull(replayed)
        assertTrue(replayed.changed)
        assertTrue(handled)
        assertEquals(PerformanceProfile.BALANCED, currentProfile)
        assertEquals("session-crash-safe", events.single().sessionId)
        assertTrue(events.single().detail.contains("memoria"))
        assertNull(pending)
        assertEquals(
            PerformanceProfile.BALANCED,
            persisted[AdaptiveGameKey("game.a", "1#1")]?.profile
        )
    }


}
