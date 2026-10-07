package com.cardenaspiero255.gamehubultra

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cardenaspiero255.gamehubultra.domain.AiProfileProposal
import com.cardenaspiero255.gamehubultra.domain.GameProfileConfig
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AiProfileBuilderCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyProposalExplainsThatNoChangeIsAvailable() {
        composeRule.setContent {
            AiProfileBuilderCard(
                proposal = null,
                canRollback = false,
                onApply = {},
                onRollback = {}
            )
        }

        composeRule.onNodeWithText("PERFIL IA").assertIsDisplayed()
        composeRule.onNodeWithText(
            "No hay cambios nuevos que proponer con la evidencia actual."
        ).assertIsDisplayed()
    }

    @Test
    fun proposalRendersVerifiedDetailsAndApplyAction() {
        val proposal = AiProfileProposal(
            version = 46,
            previousKnownGoodConfig = GameProfileConfig(),
            proposedConfig = GameProfileConfig(
                performanceProfile = PerformanceProfile.X4,
                refreshRateTargetHz = 120
            ),
            reasons = listOf("Telemetría estable"),
            disabledSettings = listOf("Interpolación no soportada"),
            requiresExplicitApply = true
        )

        var applyCalls = 0
        var rollbackCalls = 0
        composeRule.setContent {
            AiProfileBuilderCard(
                proposal = proposal,
                canRollback = true,
                onApply = { applyCalls += 1 },
                onRollback = { rollbackCalls += 1 }
            )
        }

        composeRule.onNodeWithText("Propuesta V46").assertIsDisplayed()
        composeRule.onNodeWithText("Refresco verificado: 120 Hz").assertIsDisplayed()
        composeRule.onNodeWithText("• Telemetría estable").assertIsDisplayed()
        composeRule.onNodeWithText("• Interpolación no soportada").assertIsDisplayed()
        composeRule.onNodeWithText("Aplicar propuesta IA")
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithText("Revertir última propuesta IA")
            .assertIsDisplayed()
            .performClick()
        composeRule.runOnIdle {
            assertEquals(1, applyCalls)
            assertEquals(1, rollbackCalls)
        }
    }
}
