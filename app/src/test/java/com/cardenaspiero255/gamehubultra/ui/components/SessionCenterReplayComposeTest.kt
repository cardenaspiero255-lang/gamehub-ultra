package com.cardenaspiero255.gamehubultra.ui.components

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.cardenaspiero255.gamehubultra.data.GameSessionRecord
import com.cardenaspiero255.gamehubultra.domain.PerformanceEvent
import com.cardenaspiero255.gamehubultra.domain.PerformanceEventType
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SessionCenterReplayComposeTest {
    @get:Rule val rule = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val completed = GameSessionRecord(
        id = "replay-session",
        packageName = "com.example.game",
        profileName = "BALANCED",
        startedAtMillis = 1_000L,
        endedAtMillis = 11_000L,
        startBatteryPercent = 85,
        endBatteryPercent = 80,
        endThermalStatus = 3
    )

    @Test fun completedSessionReplaysOnlyExistingSessionEventsAndMissingDataState() {
        rule.setContent {
            SessionCenterCard(
                context = context,
                sessions = listOf(completed),
                events = listOf(
                    PerformanceEvent(
                        timestampMillis = 2_000,
                        type = PerformanceEventType.POLICY_CHANGED,
                        sessionId = "replay-session",
                        profile = PerformanceProfile.X4,
                        detail = "manual_profile_selection"
                    ),
                    PerformanceEvent(
                        timestampMillis = 3_000,
                        type = PerformanceEventType.THERMAL_CHANGED,
                        sessionId = "other",
                        detail = "6"
                    )
                ),
                onClear = {},
                onShare = {}
            )
        }
        rule.onNodeWithText("VER REPLAY").performClick()
        rule.onNodeWithText("1s · Perfil manual · " + PerformanceProfile.X4.title).assertExists()
        rule.onNodeWithText("Refresco y latencia: sin historial registrado").assertExists()
        rule.onNodeWithText("0s · Inicio · perfil BALANCED · batería 85%").assertExists()
        rule.onNodeWithText("10s · Fin · batería 80% · estado térmico 3").assertExists()
        rule.onNodeWithText("OCULTAR REPLAY").performClick()
        rule.onNodeWithText("Refresco y latencia: sin historial registrado").assertDoesNotExist()
    }

    @Test fun activeSessionHasNoCompletedReplayAction() {
        rule.setContent {
            SessionCenterCard(
                context = context,
                sessions = listOf(completed.copy(endedAtMillis = null)),
                events = emptyList(),
                onClear = {},
                onShare = {}
            )
        }
        rule.onNodeWithText("VER REPLAY").assertDoesNotExist()
    }

    @Test fun clearingAllSessionsAlsoRemovesVisibleReplayControls() {
        val current = androidx.compose.runtime.mutableStateOf(listOf(completed))
        rule.setContent {
            SessionCenterCard(
                context = context,
                sessions = current.value,
                events = emptyList(),
                onClear = { current.value = emptyList() },
                onShare = {}
            )
        }
        rule.onNodeWithText("VER REPLAY").performClick()
        rule.onNodeWithText("Refresco y latencia: sin historial registrado").assertExists()
        rule.onNodeWithText(context.getString(com.cardenaspiero255.gamehubultra.R.string.session_clear)).performClick()
        rule.onNodeWithText("Refresco y latencia: sin historial registrado").assertDoesNotExist()
        rule.onNodeWithText("VER REPLAY").assertDoesNotExist()
    }
}
