package com.cardenaspiero255.gamehubultra.ai

import com.cardenaspiero255.gamehubultra.network.NetworkGameProfile
import com.cardenaspiero255.gamehubultra.network.NetworkOptimizationOutcome
import kotlin.test.Test
import kotlin.test.assertEquals

class UltraUtilityRuntimeExecutorTest {
    @Test
    fun networkUtilityExecutesRuntimeGatewayInsteadOfReturningAcknowledgementOnly() {
        var executions = 0
        val gateway = object : UltraNetworkGamingGateway {
            override fun execute(intent: UltraUtilityIntent.NetworkGamingControl): String {
                executions += 1
                return "Modo competitivo: ACTIVO"
            }

            override fun applyProfile(profile: NetworkGameProfile) =
                NetworkOptimizationOutcome.APPLIED
        }
        val answer = UltraAgentAnswer(
            message = "Entendí la orden para activar modo competitivo.",
            intent = UltraUtilityIntent.NetworkGamingControl(
                competitive = true,
                routerGaming = false
            ),
            canRunDuringGame = true
        )

        val result = UltraUtilityRuntimeExecutor.execute(answer, gateway)

        assertEquals(1, executions)
        assertEquals("Modo competitivo: ACTIVO", result)
    }

    @Test
    fun nonNetworkUtilityKeepsItsRegularAnswer() {
        val gateway = object : UltraNetworkGamingGateway {
            override fun execute(intent: UltraUtilityIntent.NetworkGamingControl): String =
                error("must not run")

            override fun applyProfile(profile: NetworkGameProfile) =
                NetworkOptimizationOutcome.NOT_REQUESTED
        }
        val answer = UltraAgentAnswer(
            message = "Son las 13:14.",
            intent = UltraUtilityIntent.CurrentTime,
            canRunDuringGame = true
        )

        assertEquals(
            "Son las 13:14.",
            UltraUtilityRuntimeExecutor.execute(answer, gateway)
        )
    }
}
