package com.cardenaspiero255.gamehubultra

import android.Manifest
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.Lifecycle
import org.junit.Rule
import org.junit.Test

/**
 * Ultra Sentinel instrumented crash-replay probe.
 *
 * Runs against an actual emulator/device with Android framework state; it does
 * not claim to reproduce arbitrary Sentry reports or vendor microphone faults.
 * These steps provide a deterministic RED target when startup/resume regresses.
 */
class MainActivitySentinelResilienceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun repeatedBackgroundResumeAndRecreateKeepNavigationAlive() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName
        instrumentation.uiAutomation.executeShellCommand(
            "pm grant ${packageName} ${Manifest.permission.POST_NOTIFICATIONS}"
        ).close()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            repeat(3) { cycle ->
                awaitHomeNavigation()
                scenario.moveToState(Lifecycle.State.STARTED)
                scenario.moveToState(Lifecycle.State.RESUMED)
                awaitHomeNavigation()
                if (cycle < 2) {
                    scenario.recreate()
                    awaitHomeNavigation()
                }
            }
        }
    }

    private fun awaitHomeNavigation() {
        compose.waitUntil(timeoutMillis = 15_000L) {
            compose.onAllNodesWithTag(
                "nav_inicio", useUnmergedTree = true
            ).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
