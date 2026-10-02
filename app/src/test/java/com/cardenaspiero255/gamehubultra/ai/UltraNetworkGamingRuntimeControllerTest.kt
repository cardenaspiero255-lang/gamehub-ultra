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
    fun routerGamingAppliesUsefulLocalPriorityInsteadOfBeingANoOp() {
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

        assertEquals(1, localCalls)
        assertTrue(message.contains("Gaming Router: ACTIVO"))
        assertTrue(message.contains("prioridad local", ignoreCase = true))
        assertFalse(message.contains("Gaming Router: NO ACTIVADO"))
    }

    @Test
    fun combinedCompetitiveAndRouterGamingAcquireLocalPriorityOnlyOnce() {
        var localCalls = 0
        val message = UltraNetworkGamingRuntimeController.execute(
            intent = UltraUtilityIntent.NetworkGamingControl(
                competitive = true,
                routerGaming = true
            ),
            applyCompetitive = {
                localCalls += 1
                NetworkOptimizationOutcome.APPLIED
            }
        )

        assertEquals(1, localCalls)
        assertTrue(message.contains("Modo competitivo: ACTIVO"))
        assertTrue(message.contains("Gaming Router: ACTIVO"))
    }
    @Test
    fun networkModesReportEveryNonAppliedRuntimeOutcomeTruthfully() {
        val cases = listOf(
            NetworkOptimizationOutcome.LEASE_ACQUIRED_PENDING_INTERACTIVE to "PREPARADO",
            NetworkOptimizationOutcome.RELEASED_OR_NOT_NEEDED to "NO ACTIVADO",
            NetworkOptimizationOutcome.UNAVAILABLE to "NO ACTIVADO",
            NetworkOptimizationOutcome.NOT_REQUESTED to "NO ACTIVADO"
        )

        cases.forEach { (outcome, expectedStatus) ->
            val competitive = UltraNetworkGamingRuntimeController.execute(
                intent = UltraUtilityIntent.NetworkGamingControl(
                    competitive = true,
                    routerGaming = false
                ),
                applyCompetitive = { outcome }
            )
            val router = UltraNetworkGamingRuntimeController.execute(
                intent = UltraUtilityIntent.NetworkGamingControl(
                    competitive = false,
                    routerGaming = true
                ),
                applyCompetitive = { outcome }
            )

            assertTrue(competitive.contains("Modo competitivo: $expectedStatus"))
            assertTrue(router.contains("Gaming Router: $expectedStatus"))
        }
    }

    @Test
    fun optimizerFailureIsContainedAndReportedAsUnavailable() {
        val message = UltraNetworkGamingRuntimeController.execute(
            intent = UltraUtilityIntent.NetworkGamingControl(
                competitive = true,
                routerGaming = true
            ),
            applyCompetitive = { error("wifi failure") }
        )

        assertTrue(message.contains("Modo competitivo: NO ACTIVADO"))
        assertTrue(message.contains("Gaming Router: NO ACTIVADO"))
    }

    @Test
    fun emptyNetworkIntentDoesNotAcquirePriority() {
        var calls = 0
        val message = UltraNetworkGamingRuntimeController.execute(
            intent = UltraUtilityIntent.NetworkGamingControl(
                competitive = false,
                routerGaming = false
            ),
            applyCompetitive = {
                calls += 1
                NetworkOptimizationOutcome.APPLIED
            }
        )

        assertEquals(0, calls)
        assertEquals("No se solicitó ningún cambio de red.", message)
    }

}
