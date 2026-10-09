package com.cardenaspiero255.gamehubultra.ui.components

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class SentinelStatusCardTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun sentinelPanelNeverFabricatesCIHealthOrRequestsSecrets() {
        composeRule.setContent { SentinelStatusCard() }

        composeRule.onNodeWithTag("sentinel_status_panel").assertExists()
        composeRule.onNodeWithText("Ultra Sentinel").assertExists()
        composeRule.onNodeWithText("Estado de CI: consultar GitHub").assertExists()
        composeRule.onNodeWithText("Ver revisiones en GitHub").assertExists()
    }
}
