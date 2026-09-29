package com.cardenaspiero255.gamehubultra

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test

class MainActivityPresentationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun activityRendersGameHubNavigationThroughPresentationBoundary() {
        composeRule
            .onNodeWithTag("nav_inicio", useUnmergedTree = true)
            .fetchSemanticsNode()
    }
}
