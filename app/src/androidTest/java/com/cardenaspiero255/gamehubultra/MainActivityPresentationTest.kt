package com.cardenaspiero255.gamehubultra

import android.Manifest
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

class MainActivityPresentationTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun activityRendersGameHubNavigationThroughPresentationBoundary() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName
        instrumentation.uiAutomation
            .executeShellCommand(
                "pm grant $packageName ${Manifest.permission.POST_NOTIFICATIONS}"
            )
            .close()

        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.waitUntil(timeoutMillis = 10_000L) {
                composeRule
                    .onAllNodesWithTag("nav_inicio", useUnmergedTree = true)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            composeRule
                .onNodeWithTag("nav_inicio", useUnmergedTree = true)
                .assertExists()
        }
    }
}
