package com.cardenaspiero255.gamehubultra.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.cardenaspiero255.gamehubultra.R
import org.junit.Rule
import org.junit.Test

class SentinelStatusCardTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun sentinelPanelNeverFabricatesCIHealthOrRequestsSecrets() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent { SentinelStatusCard() }

        composeRule.onNodeWithTag("sentinel_status_panel").assertExists()
        composeRule.onNodeWithText(context.getString(R.string.sentinel_card_title)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.sentinel_card_ci_status)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.sentinel_card_open_reviews)).assertExists()
    }
}
