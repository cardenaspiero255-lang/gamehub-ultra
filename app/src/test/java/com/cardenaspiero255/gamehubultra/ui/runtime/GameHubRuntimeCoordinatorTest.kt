package com.cardenaspiero255.gamehubultra.ui.runtime

import com.cardenaspiero255.gamehubultra.data.GameSessionRecord
import com.cardenaspiero255.gamehubultra.data.OptimizationContextKey
import com.cardenaspiero255.gamehubultra.data.RuntimeGameSession
import com.cardenaspiero255.gamehubultra.data.SessionEndMetrics
import com.cardenaspiero255.gamehubultra.data.SessionFinishHandle
import com.cardenaspiero255.gamehubultra.domain.AssistantPreset
import com.cardenaspiero255.gamehubultra.domain.OptimizationObservation
import com.cardenaspiero255.gamehubultra.domain.PerformanceEvent
import com.cardenaspiero255.gamehubultra.domain.PerformanceEventType
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.domain.ResolutionAdvice
import com.cardenaspiero255.gamehubultra.domain.SmartGameAssistantSuggestion
import com.cardenaspiero255.gamehubultra.domain.ThermalPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameHubRuntimeCoordinatorTest {

    @Test
    fun profileSelectionRoutesToSelectedGameAndRecordsOnePolicyChange() {
        val actions = FakeRuntimeActions()
        val coordinator = coordinator(actions = actions, nowMillis = 1_000L)
        val snapshot = snapshot(
            selectedGamePackage = "game.a",
            effectiveProfile = PerformanceProfile.BALANCED,
            activeSessionId = "session-a"
        )

        coordinator.selectProfile(snapshot, PerformanceProfile.X4)

        assertEquals(listOf("game.a" to PerformanceProfile.X4), actions.gameProfileSelections)
        assertEquals(emptyList(), actions.globalProfileSelections)
        assertEquals(1, actions.events.size)
        val event = actions.events.single()
        assertEquals(PerformanceEventType.POLICY_CHANGED, event.type)
        assertEquals("session-a", event.sessionId)
        assertEquals(PerformanceProfile.X4, event.profile)
        assertEquals("manual_profile_selection", event.detail)
    }

    @Test
    fun selectingCurrentProfileDoesNotCreateDuplicatePolicyEvent() {
        val actions = FakeRuntimeActions()
        val coordinator = coordinator(actions = actions, nowMillis = 2_000L)
        val snapshot = snapshot(
            selectedGamePackage = null,
            effectiveProfile = PerformanceProfile.BALANCED,
            activeSessionId = null
        )

        coordinator.selectProfile(snapshot, PerformanceProfile.BALANCED)

        assertEquals(listOf(PerformanceProfile.BALANCED), actions.globalProfileSelections)
        assertTrue(actions.events.isEmpty())
    }

    @Test
    fun endingSessionRecordsThermalOutcomeOnlyAfterFinishSucceeds() {
        val completedJob = Job().apply { complete() }
        val actions = FakeRuntimeActions().apply {
            finishResult = SessionFinishHandle(
                session = RuntimeGameSession(
                    id = "session-old",
                    packageName = "game.old"
                ),
                job = completedJob
            )
        }
        val observations = mutableListOf<Pair<OptimizationContextKey, OptimizationObservation>>()
        val coordinator = GameHubRuntimeCoordinator(
            scope = CoroutineScope(Job() + Dispatchers.Unconfined),
            actions = actions,
            recordOptimization = { key, observation ->
                observations += key to observation
            },
            optimizationDispatcher = Dispatchers.Unconfined,
            nowMillis = { 3_000L },
            sessionIdFactory = { "unused" }
        )
        val snapshot = snapshot(
            selectedGamePackage = "game.old",
            effectiveProfile = PerformanceProfile.FRAME_INTERPOLATION,
            activeSessionId = "session-old",
            metrics = RuntimeSessionMetrics(
                batteryPercent = 42,
                thermalStatus = 4,
                ramUsedPercent = 88
            )
        )

        val handle = coordinator.endGameSession(snapshot)

        assertEquals("session-old", handle?.session?.id)
        assertEquals(
            SessionEndMetrics(
                endedAtMillis = 3_000L,
                endBatteryPercent = 42,
                endThermalStatus = 4,
                endRamUsedPercent = 88
            ),
            actions.finishMetrics.single()
        )
        assertEquals(PerformanceEventType.SESSION_ENDED, actions.events.single().type)
        assertEquals("game.old", actions.events.single().detail)

        val observation = observations.single().second
        assertEquals(PerformanceProfile.FRAME_INTERPOLATION, observation.profile)
        assertFalse(observation.stable)
        assertTrue(observation.failed)
        assertTrue(observation.highTemperature)
        assertEquals(4, observation.thermalStatus)
        assertEquals(42, observation.batteryPercent)
        assertEquals("thermal_pressure", observation.errorReason)
        assertEquals(3_000L, observation.timestampMillis)
    }

    @Test
    fun endingSessionWithoutDiagnosticsDoesNotClaimStableObservation() {
        val completedJob = Job().apply { complete() }
        val actions = FakeRuntimeActions().apply {
            finishResult = SessionFinishHandle(
                session = RuntimeGameSession(
                    id = "session-no-telemetry",
                    packageName = "game.a"
                ),
                job = completedJob
            )
        }
        val observations = mutableListOf<OptimizationObservation>()
        val coordinator = GameHubRuntimeCoordinator(
            scope = CoroutineScope(Job() + Dispatchers.Unconfined),
            actions = actions,
            recordOptimization = { _, observation ->
                observations += observation
            },
            optimizationDispatcher = Dispatchers.Unconfined,
            nowMillis = { 3_500L },
            sessionIdFactory = { "unused" }
        )

        coordinator.endGameSession(
            snapshot(
                selectedGamePackage = "game.a",
                activeSessionId = "session-no-telemetry",
                metrics = RuntimeSessionMetrics(
                    diagnosticsAvailable = false
                )
            )
        )

        val observation = observations.single()
        assertFalse(observation.stable)
        assertFalse(observation.failed)
        assertFalse(observation.highTemperature)
        assertNull(observation.thermalStatus)
    }

    @Test
    fun recordingOpenedGameStartsDeterministicSessionAndRecentGame() {
        val actions = FakeRuntimeActions()
        val coordinator = GameHubRuntimeCoordinator(
            scope = CoroutineScope(Job() + Dispatchers.Unconfined),
            actions = actions,
            recordOptimization = { _, _ -> },
            optimizationDispatcher = Dispatchers.Unconfined,
            nowMillis = { 4_000L },
            sessionIdFactory = { "session-new" }
        )
        val snapshot = snapshot(
            selectedGamePackage = "game.a",
            effectiveProfile = PerformanceProfile.BALANCED,
            activeSessionId = null,
            metrics = RuntimeSessionMetrics(batteryPercent = 73)
        )

        coordinator.recordGameOpened(snapshot, "game.b")

        val record = actions.startedSessions.single()
        assertEquals("session-new", record.id)
        assertEquals("game.b", record.packageName)
        assertEquals(PerformanceProfile.BALANCED.name, record.profileName)
        assertEquals(4_000L, record.startedAtMillis)
        assertEquals(73, record.startBatteryPercent)
        assertEquals(listOf("game.b"), actions.recentGames)

        val started = actions.events.single()
        assertEquals(PerformanceEventType.SESSION_STARTED, started.type)
        assertEquals("session-new", started.sessionId)
        assertEquals("game.b", started.detail)
    }

    @Test
    fun suggestionUsesGameSpecificPersistenceWhenGameIsSelected() {
        val actions = FakeRuntimeActions()
        val coordinator = coordinator(actions = actions, nowMillis = 5_000L)
        val suggestion = SmartGameAssistantSuggestion(
            preset = AssistantPreset.RECOMMENDED,
            profile = PerformanceProfile.X4,
            thermalPreference = ThermalPreference.ADAPTIVE,
            refreshRateTargetHz = 120,
            resolutionAdvice = ResolutionAdvice.AUTO,
            reason = "test",
            evidence = emptyList()
        )

        coordinator.applySmartGameAssistantSuggestion(
            snapshot(selectedGamePackage = "game.a"),
            suggestion
        )

        assertEquals(listOf("game.a" to suggestion), actions.smartSuggestions)
        assertTrue(actions.globalProfileSelections.isEmpty())
    }

    @Test
    fun selectGameEndsCurrentSessionBeforePersistingNewSelection() {
        val actions = FakeRuntimeActions()
        val order = actions.order
        val coordinator = coordinator(actions = actions, nowMillis = 6_000L)

        coordinator.selectGame(
            snapshot(selectedGamePackage = "game.old"),
            "game.new"
        )

        assertEquals(listOf("finish", "select:game.new"), order)
    }

    private fun coordinator(
        actions: FakeRuntimeActions,
        nowMillis: Long
    ) = GameHubRuntimeCoordinator(
        scope = CoroutineScope(Job() + Dispatchers.Unconfined),
        actions = actions,
        recordOptimization = { _, _ -> },
        optimizationDispatcher = Dispatchers.Unconfined,
        nowMillis = { nowMillis },
        sessionIdFactory = { "session-generated" }
    )

    private fun snapshot(
        selectedGamePackage: String? = null,
        effectiveProfile: PerformanceProfile = PerformanceProfile.BALANCED,
        activeSessionId: String? = null,
        metrics: RuntimeSessionMetrics = RuntimeSessionMetrics(
            diagnosticsAvailable = true
        ),
        optimizationContextKey: OptimizationContextKey = OptimizationContextKey(
            deviceFingerprint = "device",
            gamePackage = selectedGamePackage.orEmpty(),
            gameVersion = "1",
            emulatorBackend = null,
            driverFingerprint = "driver"
        )
    ) = GameHubRuntimeSnapshot(
        selectedGamePackage = selectedGamePackage,
        effectiveProfile = effectiveProfile,
        activeSessionId = activeSessionId,
        metrics = metrics,
        optimizationContextKey = optimizationContextKey
    )

    private class FakeRuntimeActions : GameHubRuntimeActions {
        val globalProfileSelections = mutableListOf<PerformanceProfile>()
        val gameProfileSelections = mutableListOf<Pair<String, PerformanceProfile>>()
        val smartSuggestions = mutableListOf<Pair<String, SmartGameAssistantSuggestion>>()
        val finishMetrics = mutableListOf<SessionEndMetrics>()
        val startedSessions = mutableListOf<GameSessionRecord>()
        val events = mutableListOf<PerformanceEvent>()
        val recentGames = mutableListOf<String>()
        val selectedGames = mutableListOf<String>()
        val order = mutableListOf<String>()
        var finishResult: SessionFinishHandle? = null

        override fun selectGlobalProfile(profile: PerformanceProfile) {
            globalProfileSelections += profile
        }

        override fun selectGameProfile(packageName: String, profile: PerformanceProfile) {
            gameProfileSelections += packageName to profile
        }

        override fun applySmartGameAssistantSuggestion(
            packageName: String,
            suggestion: SmartGameAssistantSuggestion
        ) {
            smartSuggestions += packageName to suggestion
        }

        override fun finishRuntimeGameSession(metrics: SessionEndMetrics): SessionFinishHandle? {
            finishMetrics += metrics
            order += "finish"
            return finishResult
        }

        override fun beginRuntimeGameSession(record: GameSessionRecord) {
            startedSessions += record
        }

        override fun recordPerformanceEvent(event: PerformanceEvent) {
            events += event
        }

        override fun recordRecentGame(packageName: String) {
            recentGames += packageName
        }

        override fun selectGame(packageName: String) {
            selectedGames += packageName
            order += "select:$packageName"
        }
    }
}
