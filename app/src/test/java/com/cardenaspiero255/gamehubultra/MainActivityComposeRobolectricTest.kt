package com.cardenaspiero255.gamehubultra

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityComposeRobolectricTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun productionHomeComposesAiProfileBuilderIntegration() {
        composeRule.waitForIdle()

        composeRule.onNodeWithText("PERFIL IA").assertIsDisplayed()
        composeRule.onNodeWithText(
            "No hay cambios nuevos que proponer con la evidencia actual."
        ).assertIsDisplayed()
    }
}
