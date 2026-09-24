package com.cardenaspiero255.gamehubultra

import kotlin.test.Test
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
}
