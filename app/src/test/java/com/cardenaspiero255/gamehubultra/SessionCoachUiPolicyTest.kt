package com.cardenaspiero255.gamehubultra

import com.cardenaspiero255.gamehubultra.data.GameSessionRecord
import com.cardenaspiero255.gamehubultra.data.SessionCoachStoredSession
import kotlin.test.Test
import kotlin.test.assertFalse
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
