package com.cardenaspiero255.gamehubultra

import com.cardenaspiero255.gamehubultra.data.SessionCoachStoredSession
import com.cardenaspiero255.gamehubultra.ai.UltraFrontierWorldStateRegistry
import com.cardenaspiero255.gamehubultra.domain.BatteryGamingRecommendation
import com.cardenaspiero255.gamehubultra.domain.ThermalRisk
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
    fun completedSessionPublishesThermalAndBatterySignalsToFrontierWorldState() {
        UltraFrontierWorldStateRegistry.clear()
        val optimizer = PerGameAdaptiveOptimizer(
            confirmationsRequired = 1,
            cooldownMillis = 0
        )
        val samples = listOf(
            SessionCoachSnapshot(
                timestampMillis = 1_000L,
                batteryPercent = 24,
                thermalStatus = 1,
                thermalHeadroom = 0.55f,
                refreshRateHz = 120f,
                latencyMs = 30L,
                memoryUsedPercent = 60
            ),
            SessionCoachSnapshot(
                timestampMillis = 12_000L,
                batteryPercent = 23,
                thermalStatus = 2,
                thermalHeadroom = 0.62f,
                refreshRateHz = 120f,
                latencyMs = 31L,
                memoryUsedPercent = 62
            ),
            SessionCoachSnapshot(
                timestampMillis = 23_000L,
                batteryPercent = 22,
                thermalStatus = 2,
                thermalHeadroom = 0.69f,
                refreshRateHz = 120f,
                latencyMs = 32L,
                memoryUsedPercent = 64
            ),
            SessionCoachSnapshot(
                timestampMillis = 34_000L,
                batteryPercent = 21,
                thermalStatus = 3,
                thermalHeadroom = 0.76f,
                refreshRateHz = 120f,
                latencyMs = 33L,
                memoryUsedPercent = 66
            ),
            SessionCoachSnapshot(
                timestampMillis = 45_000L,
                batteryPercent = 20,
                thermalStatus = 3,
                thermalHeadroom = 0.83f,
                refreshRateHz = 120f,
                latencyMs = 34L,
                memoryUsedPercent = 68
            )
        )

        applyCompletedAdaptiveDecision(
            completed = SessionCoachStoredSession(
                sessionId = "frontier-world-state",
                packageName = "game.frontier",
                startedAtMillis = 1_000L,
                endedAtMillis = 45_000L,
                samples = samples,
                preSessionMessage = null,
                latestObservation = null,
                gameVersion = "1"
            ),
            activeProfile = PerformanceProfile.X4,
            optimizer = optimizer,
            nowMillis = 46_000L,
            applyProfile = { _, _ -> },
            recordPerformanceEvent = {}
        )

        val world = UltraFrontierWorldStateRegistry.snapshot()
        assertEquals("game.frontier", world?.selectedGamePackage)
        assertTrue(
            world?.thermalRisk == ThermalRisk.HIGH ||
                world?.thermalRisk == ThermalRisk.CRITICAL
        )
        assertEquals(
            BatteryGamingRecommendation.BALANCED,
            world?.batteryRecommendation
        )
        assertTrue(world?.preventAggressiveProfiles == true)
        UltraFrontierWorldStateRegistry.clear()
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


    @Test
    fun completedSessionThermalPredictionFlowsThroughCar47BeforeMeasuredSevereStatus() {
        val optimizer = PerGameAdaptiveOptimizer(
            confirmationsRequired = 1,
            cooldownMillis = 0
        )
        val completed = SessionCoachStoredSession(
            sessionId = "session-thermal-prediction",
            packageName = "game.thermal",
            startedAtMillis = 0L,
            endedAtMillis = 40_000L,
            samples = listOf(
                snapshot(0L, memory = 50).copy(thermalHeadroom = 0.46f),
                snapshot(10_000L, memory = 50).copy(thermalHeadroom = 0.52f),
                snapshot(20_000L, memory = 50).copy(thermalHeadroom = 0.59f),
                snapshot(30_000L, memory = 50).copy(thermalHeadroom = 0.66f),
                snapshot(40_000L, memory = 50).copy(thermalHeadroom = 0.74f)
            ),
            preSessionMessage = null,
            latestObservation = null,
            gameVersion = "1#1"
        )
        var applied: Pair<String, PerformanceProfile>? = null

        val decision = applyCompletedAdaptiveDecision(
            completed = completed,
            activeProfile = PerformanceProfile.X4,
            optimizer = optimizer,
            nowMillis = 41_000L,
            applyProfile = { packageName, profile ->
                applied = packageName to profile
            },
            recordPerformanceEvent = {}
        )

        assertNotNull(decision)
        assertTrue(decision.changed)
        assertEquals(PerformanceProfile.BALANCED, decision.profile)
        assertEquals("game.thermal" to PerformanceProfile.BALANCED, applied)
        assertTrue(decision.reason.contains("predicción térmica", ignoreCase = true))
    }


    @Test
    fun completedSessionUsesTimestampLatestSampleWhenHistoryArrivesOutOfOrder() {
        val optimizer = PerGameAdaptiveOptimizer(
            confirmationsRequired = 1,
            cooldownMillis = 0
        )
        val completed = SessionCoachStoredSession(
            sessionId = "session-out-of-order",
            packageName = "game.order",
            startedAtMillis = 0L,
            endedAtMillis = 30_000L,
            samples = listOf(
                snapshot(30_000L, memory = 95),
                snapshot(10_000L, memory = 40),
                snapshot(20_000L, memory = 40)
            ),
            preSessionMessage = null,
            latestObservation = null,
            gameVersion = "1"
        )

        val decision = applyCompletedAdaptiveDecision(
            completed = completed,
            activeProfile = PerformanceProfile.X4,
            optimizer = optimizer,
            nowMillis = 31_000L,
            applyProfile = { _, _ -> },
            recordPerformanceEvent = {}
        )

        assertNotNull(decision)
        assertTrue(decision.changed)
        assertEquals(PerformanceProfile.BALANCED, decision.profile)
        assertTrue(decision.reason.contains("memoria", ignoreCase = true))
    }


    @Test
    fun completedSessionBatteryConstraintFlowsIntoAdaptiveDecisionReason() {
        val optimizer = PerGameAdaptiveOptimizer(
            confirmationsRequired = 1,
            cooldownMillis = 0
        )
        val completed = SessionCoachStoredSession(
            sessionId = "session-battery-constraint",
            packageName = "game.battery",
            startedAtMillis = 0L,
            endedAtMillis = 20 * 60_000L,
            samples = listOf(
                snapshot(0L, memory = 40).copy(
                    batteryPercent = 80,
                    batteryCharging = false,
                    powerSaveMode = false
                ),
                snapshot(10 * 60_000L, memory = 40).copy(
                    batteryPercent = 76,
                    batteryCharging = false,
                    powerSaveMode = false
                ),
                snapshot(20 * 60_000L, memory = 40).copy(
                    batteryPercent = 72,
                    batteryCharging = false,
                    powerSaveMode = false
                )
            ),
            preSessionMessage = null,
            latestObservation = null,
            gameVersion = "1"
        )

        val decision = applyCompletedAdaptiveDecision(
            completed = completed,
            activeProfile = PerformanceProfile.X4,
            optimizer = optimizer,
            nowMillis = 21 * 60_000L,
            applyProfile = { _, _ -> },
            recordPerformanceEvent = {}
        )

        assertNotNull(decision)
        assertTrue(decision.changed)
        assertEquals(PerformanceProfile.BALANCED, decision.profile)
        assertTrue(decision.reason.contains("drenaje", ignoreCase = true))
    }


    @Test
    fun chargingAtCriticalBatteryDoesNotTriggerAdaptiveDownshift() {
        val optimizer = PerGameAdaptiveOptimizer(
            confirmationsRequired = 1,
            cooldownMillis = 0
        )
        val completed = SessionCoachStoredSession(
            sessionId = "session-charging-critical",
            packageName = "game.charging",
            startedAtMillis = 0L,
            endedAtMillis = 10_000L,
            samples = listOf(
                snapshot(0L, memory = 40).copy(
                    batteryPercent = 12,
                    batteryCharging = true,
                    powerSaveMode = false
                ),
                snapshot(10_000L, memory = 40).copy(
                    batteryPercent = 10,
                    batteryCharging = true,
                    powerSaveMode = false
                )
            ),
            preSessionMessage = null,
            latestObservation = null,
            gameVersion = "1"
        )

        val decision = applyCompletedAdaptiveDecision(
            completed = completed,
            activeProfile = PerformanceProfile.X4,
            optimizer = optimizer,
            nowMillis = 11_000L,
            applyProfile = { _, _ -> },
            recordPerformanceEvent = {}
        )

        assertNotNull(decision)
        assertFalse(decision.changed)
        assertEquals(PerformanceProfile.X4, decision.profile)
        assertFalse(decision.reason.contains("batería crítica", ignoreCase = true))
    }

}
