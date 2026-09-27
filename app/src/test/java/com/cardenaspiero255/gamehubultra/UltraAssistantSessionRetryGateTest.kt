package com.cardenaspiero255.gamehubultra

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UltraAssistantSessionRetryGateTest {

    @Test
    fun failedLoadGetsOnlyOneAutomaticRetryPerGameScope() {
        val gate = UltraAssistantSessionRetryGate(maxRetriesPerScope = 1)

        assertTrue(gate.consumeRetry("game.a", hasLoadError = true))
        assertFalse(gate.consumeRetry("game.a", hasLoadError = true))
        assertFalse(gate.consumeRetry("game.a", hasLoadError = false))
    }

    @Test
    fun changingGameScopeRestoresTheAutomaticRetryBudget() {
        val gate = UltraAssistantSessionRetryGate(maxRetriesPerScope = 1)

        assertTrue(gate.consumeRetry("game.a", hasLoadError = true))
        assertFalse(gate.consumeRetry("game.a", hasLoadError = true))
        assertTrue(gate.consumeRetry("game.b", hasLoadError = true))
        assertFalse(gate.consumeRetry("game.b", hasLoadError = true))
    }

    @Test
    fun globalScopeAndGameScopeHaveIndependentRetryBudgets() {
        val gate = UltraAssistantSessionRetryGate(maxRetriesPerScope = 1)

        assertTrue(gate.consumeRetry(null, hasLoadError = true))
        assertFalse(gate.consumeRetry(null, hasLoadError = true))
        assertTrue(gate.consumeRetry("game.a", hasLoadError = true))
    }
}
