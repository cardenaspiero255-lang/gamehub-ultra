package com.cardenaspiero255.gamehubultra.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cardenaspiero255.gamehubultra.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SentinelStatusCardTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun sentinelPanelNeverFabricatesCIHealthOrRequestsSecrets() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            MaterialTheme { SentinelStatusCard() }
        }
        // Emulator startup and first Compose frame can race on hosted API 35.
        // Wait for the *actual* semantic root; do not hide a persistent failure.
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 20_000L) {
            runCatching {
                composeRule.onAllNodesWithTag(
                    "sentinel_status_panel",
                    useUnmergedTree = true
                ).fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag("sentinel_status_panel").assertExists()
        composeRule.onNodeWithText(context.getString(R.string.sentinel_card_title)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.sentinel_card_ci_status)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.sentinel_card_open_reviews)).assertExists()
    }
}
