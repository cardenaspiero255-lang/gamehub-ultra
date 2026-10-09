package com.cardenaspiero255.gamehubultra.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** JaCoCo JVM coverage for the real Compose panel; instrumentation-only UI
 * tests are not part of the testDebugUnitTest coverage aggregation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SentinelStatusCardCoverageTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun readOnlySentinelPanelExplainsEvidenceAndLinksToTrustedGithub() {
        val visited = mutableListOf<String>()
        val handler = object : UriHandler {
            override fun openUri(uri: String) {
                visited += uri
            }
        }
        composeRule.setContent {
            CompositionLocalProvider(LocalUriHandler provides handler) {
                SentinelStatusCard()
            }
        }
        composeRule.onNodeWithTag("sentinel_status_panel").assertExists()
        composeRule.onNodeWithText("Ultra Sentinel").assertExists()
        composeRule.onNodeWithText("Estado de CI: consultar GitHub").assertExists()
        composeRule.onNodeWithText("Ver revisiones en GitHub").performClick()
        composeRule.waitForIdle()
        assertEquals(
            listOf("https://github.com/cardenaspiero255-lang/gamehub-ultra/actions"),
            visited
        )
    }
}
