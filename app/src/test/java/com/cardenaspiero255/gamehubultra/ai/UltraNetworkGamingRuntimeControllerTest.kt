package com.cardenaspiero255.gamehubultra.ai

import com.cardenaspiero255.gamehubultra.network.NetworkOptimizationOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UltraNetworkGamingRuntimeControllerTest {
    @Test
    fun competitiveModeReportsActiveOnlyAfterOptimizerApplies() {
        var calls = 0
        val message = UltraNetworkGamingRuntimeController.execute(
            intent = UltraUtilityIntent.NetworkGamingControl(
                competitive = true,
                routerGaming = false
            ),
            applyCompetitive = {
                calls += 1
                NetworkOptimizationOutcome.APPLIED
            }
        )

        assertEquals(1, calls)
        assertTrue(message.contains("Modo competitivo: ACTIVO"))
    }

    @Test
    fun competitiveModeDoesNotClaimActiveWhenOptimizerIsUnavailable() {
        val message = UltraNetworkGamingRuntimeController.execute(
            intent = UltraUtilityIntent.NetworkGamingControl(
                competitive = true,
                routerGaming = false
            ),
            applyCompetitive = { NetworkOptimizationOutcome.UNAVAILABLE }
        )

        assertTrue(message.contains("Modo competitivo: NO ACTIVADO"))
        assertFalse(message.contains("Modo competitivo: ACTIVO"))
    }

    @Test
    fun routerGamingWithoutAuthorizedIntegrationIsExplicitlyNotActivated() {
        var localCalls = 0
        val message = UltraNetworkGamingRuntimeController.execute(
            intent = UltraUtilityIntent.NetworkGamingControl(
                competitive = false,
                routerGaming = true
            ),
            applyCompetitive = {
                localCalls += 1
                NetworkOptimizationOutcome.APPLIED
            }
        )

        assertEquals(0, localCalls)
        assertTrue(message.contains("Gaming Router: NO ACTIVADO"))
        assertTrue(message.contains("integración autorizada"))
    }
}
