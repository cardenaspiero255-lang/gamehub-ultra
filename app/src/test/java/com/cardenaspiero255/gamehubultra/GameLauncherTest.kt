package com.cardenaspiero255.gamehubultra

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameLauncherTest {
    @Test
    fun blankPackageIsRejectedBeforeResolution() {
        var resolved = false
        assertFalse(
            GameLauncher.resolveAndLaunch(
                packageName = "",
                resolver = { resolved = true; "resolved-intent" },
                starter = {}
            )
        )
        assertFalse(resolved)
    }

    @Test
    fun successfulLaunchReturnsTrue() {
        var started = false

        assertTrue(
            GameLauncher.resolveAndLaunch(
                packageName = "game.package",
                resolver = { "resolved-intent" },
                starter = { started = true }
            )
        )
        assertTrue(started)
    }

    @Test
    fun missingLaunchTargetReturnsFalse() {
        var started = false

        assertFalse(
            GameLauncher.resolveAndLaunch(
                packageName = "missing.package",
                resolver = { null },
                starter = { started = true }
            )
        )
        assertFalse(started)
    }

    @Test
    fun resolverFailureReturnsFalse() {
        var started = false

        assertFalse(
            GameLauncher.resolveAndLaunch(
                packageName = "broken.package",
                resolver = { throw IllegalStateException("resolver unavailable") },
                starter = { started = true }
            )
        )
        assertFalse(started)
    }

    @Test
    fun launcherFailureReturnsFalse() {
        assertFalse(
            GameLauncher.resolveAndLaunch(
                packageName = "blocked.package",
                resolver = { "resolved-intent" },
                starter = { throw SecurityException("blocked") }
            )
        )
    }
    @Test
    fun launchHooksStartMonitoringBeforeGameAndDoNotCleanupOnSuccess() {
        val order = mutableListOf<String>()

        assertTrue(
            GameLauncher.resolveAndLaunch(
                packageName = "game.package",
                resolver = { "resolved-intent" },
                starter = { order += "game" },
                beforeStart = { order += "coach" },
                onStartFailure = { order += "cleanup" }
            )
        )

        assertEquals(listOf("coach", "game"), order)
    }

    @Test
    fun launchFailureCleansUpCoachSession() {
        val order = mutableListOf<String>()

        assertFalse(
            GameLauncher.resolveAndLaunch(
                packageName = "game.package",
                resolver = { "resolved-intent" },
                starter = {
                    order += "game"
                    throw SecurityException("blocked")
                },
                beforeStart = { order += "coach" },
                onStartFailure = { order += "cleanup" }
            )
        )

        assertEquals(listOf("coach", "game", "cleanup"), order)
    }

    @Test
    fun coachStartupFailureDoesNotBlockAValidGameLaunch() {
        var gameStarted = false

        assertTrue(
            GameLauncher.resolveAndLaunch(
                packageName = "game.package",
                resolver = { "resolved-intent" },
                starter = { gameStarted = true },
                beforeStart = { error("coach unavailable") }
            )
        )

        assertTrue(gameStarted)
    }

}
