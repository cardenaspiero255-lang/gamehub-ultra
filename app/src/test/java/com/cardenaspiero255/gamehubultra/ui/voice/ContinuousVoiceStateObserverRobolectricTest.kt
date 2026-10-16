package com.cardenaspiero255.gamehubultra.ui.voice

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.cardenaspiero255.gamehubultra.voice.AndroidContinuousVoiceGateway
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ContinuousVoiceStateObserverRobolectricTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun externalForegroundDenialUpdatesObservedSwitchWithoutRecreatingComposable() {
        val app = RuntimeEnvironment.getApplication()
        val uiGateway = AndroidContinuousVoiceGateway(app)
        val serviceGateway = AndroidContinuousVoiceGateway(app)
        serviceGateway.setEnabled(true)

        composeRule.setContent {
            var enabled by remember { mutableStateOf(uiGateway.isEnabled()) }
            ContinuousVoiceStateObserver(uiGateway) { enabled = it }
            Text(if (enabled) "Micrófono activo" else "Micrófono detenido")
        }
        composeRule.onNodeWithText("Micrófono activo").assertExists()

        composeRule.runOnIdle { serviceGateway.setEnabled(false) }
        composeRule.onNodeWithText("Micrófono detenido").assertExists()

        composeRule.runOnIdle { serviceGateway.setEnabled(true) }
        composeRule.onNodeWithText("Micrófono activo").assertExists()
    }
}
