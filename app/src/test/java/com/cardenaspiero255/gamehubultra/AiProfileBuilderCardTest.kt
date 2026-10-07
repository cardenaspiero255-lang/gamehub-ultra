package com.cardenaspiero255.gamehubultra

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.cardenaspiero255.gamehubultra.domain.AiProfileProposal
import com.cardenaspiero255.gamehubultra.domain.GameProfileConfig
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
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

        composeRule.setContent {
            AiProfileBuilderCard(
                proposal = proposal,
                canRollback = true,
                onApply = {},
                onRollback = {}
            )
        }

        composeRule.onNodeWithText("Propuesta V46").assertIsDisplayed()
        composeRule.onNodeWithText("Refresco verificado: 120 Hz").assertIsDisplayed()
        composeRule.onNodeWithText("• Telemetría estable").assertIsDisplayed()
        composeRule.onNodeWithText("• Interpolación no soportada").assertIsDisplayed()
        composeRule.onNodeWithText("Aplicar propuesta IA").assertIsDisplayed()
        composeRule.onNodeWithText("Revertir última propuesta IA").assertIsDisplayed()
    }
}
