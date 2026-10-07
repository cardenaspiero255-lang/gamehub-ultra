package com.cardenaspiero255.gamehubultra

import com.cardenaspiero255.gamehubultra.data.GameSessionRecord
import com.cardenaspiero255.gamehubultra.data.SessionCoachStoredSession
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

    private fun stored(
        sessionId: String,
        packageName: String,
        startedAtMillis: Long
    ) = SessionCoachStoredSession(
        sessionId = sessionId,
        packageName = packageName,
        startedAtMillis = startedAtMillis,
        endedAtMillis = startedAtMillis + 1_000L,
        samples = emptyList(),
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
