package com.cardenaspiero255.gamehubultra

import com.cardenaspiero255.gamehubultra.data.GameSessionRecord
import com.cardenaspiero255.gamehubultra.data.SessionCoachStoredSession
import com.cardenaspiero255.gamehubultra.domain.SessionCoachSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SessionCoachUiPolicyTest {
    @Test
    fun oldCompletedCoachSessionCannotEndNewRuntimeSession() {
        val completed = stored(
            sessionId = "coach-old",
            packageName = "game.a",
            startedAtMillis = 1_000L
        )
        val active = runtime(
            id = "runtime-new",
            packageName = "game.a",
            startedAtMillis = 5_000L
        )

        assertFalse(completedCoachBelongsToRuntimeSession(completed, active))
    }

    @Test
    fun matchingPackageAndStartWindowCanCloseRuntimeSession() {
        val completed = stored(
            sessionId = "coach-current",
            packageName = "game.a",
            startedAtMillis = 5_000L
        )
        val active = runtime(
            id = "runtime-current",
            packageName = "game.a",
            startedAtMillis = 5_000L
        )

        assertTrue(completedCoachBelongsToRuntimeSession(completed, active))
    }

    @Test
    fun differentPackageNeverClosesRuntimeSession() {
        assertFalse(
            completedCoachBelongsToRuntimeSession(
                stored("coach", "game.old", 5_000L),
                runtime("runtime", "game.new", 5_000L)
            )
        )
    }

    @Test
    fun completedCoachPresentationRehydratesEvenWhenSessionWasAlreadyHandled() {
        val completed = stored(
            sessionId = "coach-current",
            packageName = "game.a",
            startedAtMillis = 5_000L
        )

        val hydration = buildCompletedCoachHydration(
            completed = completed,
            hydratedSessionId = "coach-current",
            activeRuntimeRecord = null
        )

        assertNotNull(hydration)
        assertNotNull(hydration.report)
        assertTrue(hydration.observations.isEmpty())
        assertFalse(hydration.shouldEndRuntimeSession)
        assertFalse(hydration.shouldMarkHydrated)
    }

    @Test
    fun completedCoachSessionIsNotMarkedHandledBeforeSelectionHydrates() {
        val completed = stored(
            sessionId = "coach-cold-start",
            packageName = "game.a",
            startedAtMillis = 5_000L
        )

        val early = buildCompletedCoachHydration(
            completed = completed,
            hydratedSessionId = null,
            activeRuntimeRecord = null,
            selectionHydrated = false
        )
        assertFalse(early.shouldMarkHydrated)

        val ready = buildCompletedCoachHydration(
            completed = completed,
            hydratedSessionId = null,
            activeRuntimeRecord = null,
            selectionHydrated = true
        )
        assertTrue(ready.shouldMarkHydrated)
    }

    @Test
    fun storedCoachReportWinsOverDashboardFallback() {
        val storedReport = com.cardenaspiero255.gamehubultra.domain.AiSessionCoach.postSession(
            emptyList()
        )
        val dashboardReport = storedReport.copy(summary = "dashboard")

        assertEquals(
            storedReport,
            chooseCoachReport(
                storedReport = storedReport,
                dashboardReport = dashboardReport
            )
        )
        assertEquals(
            dashboardReport,
            chooseCoachReport(
                storedReport = null,
                dashboardReport = dashboardReport
            )
        )
    }


    @Test
    fun aiProfileUsesPersistedGameplaySamplesForMatchingSelectedGame() {
        val persisted = listOf(
            SessionCoachSnapshot(
                timestampMillis = 10L,
                batteryPercent = 80,
                thermalStatus = 1,
                thermalHeadroom = 0.2f,
                refreshRateHz = 120f,
                latencyMs = 20L
            )
        )
        val dashboard = listOf(
            SessionCoachSnapshot(
                timestampMillis = 20L,
                batteryPercent = 70,
                thermalStatus = 2,
                thermalHeadroom = 0.1f,
                refreshRateHz = 60f,
                latencyMs = 40L
            )
        )
        val completed = stored(
            sessionId = "coach-game-a",
            packageName = "game.a",
            startedAtMillis = 5_000L,
            samples = persisted
        )

        assertEquals(
            persisted,
            aiProfileSamplesForSelectedGame(
                completed = completed,
                selectedPackage = "game.a",
                activeSessionPackage = null,
                dashboardSamples = dashboard
            )
        )
    }

    @Test
    fun aiProfileTelemetryNeverLeaksAcrossGames() {
        val foreignSample = SessionCoachSnapshot(
            timestampMillis = 10L,
            batteryPercent = 80,
            thermalStatus = 1,
            thermalHeadroom = 0.2f,
            refreshRateHz = 120f,
            latencyMs = 20L
        )
        val completed = stored(
            sessionId = "coach-old",
            packageName = "game.old",
            startedAtMillis = 5_000L,
            samples = listOf(foreignSample)
        )

        assertTrue(
            aiProfileSamplesForSelectedGame(
                completed = completed,
                selectedPackage = "game.new",
                activeSessionPackage = "game.other",
                dashboardSamples = listOf(foreignSample)
            ).isEmpty()
        )
    }

    @Test
    fun activeSelectedGameCanUseLiveDashboardSamplesBeforeCompletion() {
        val live = listOf(
            SessionCoachSnapshot(
                timestampMillis = 30L,
                batteryPercent = 90,
                thermalStatus = 0,
                thermalHeadroom = 0.3f,
                refreshRateHz = 90f,
                latencyMs = 15L
            )
        )

        assertEquals(
            live,
            aiProfileSamplesForSelectedGame(
                completed = null,
                selectedPackage = "game.active",
                activeSessionPackage = "game.active",
                dashboardSamples = live
            )
        )
    }

    private fun stored(
        sessionId: String,
        packageName: String,
        startedAtMillis: Long,
        samples: List<SessionCoachSnapshot> = emptyList()
    ) = SessionCoachStoredSession(
        sessionId = sessionId,
        packageName = packageName,
        startedAtMillis = startedAtMillis,
        endedAtMillis = startedAtMillis + 1_000L,
        samples = samples,
        preSessionMessage = null,
        latestObservation = null
    )

    private fun runtime(
        id: String,
        packageName: String,
        startedAtMillis: Long
    ) = GameSessionRecord(
        id = id,
        packageName = packageName,
        profileName = "BALANCED",
        startedAtMillis = startedAtMillis
    )
}
