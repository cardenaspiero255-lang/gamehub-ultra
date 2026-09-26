package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class UltraMathEngineCar73ExtendedTest {

    @Test
    fun fractionAdditionRunsOffline() {
        val solution = assertNotNull(
            UltraMathEngine.solve("Ultra, suma tres cuartos más un cuarto")
        )

        assertEquals("1", solution.resultText)
        assertFalse(solution.requiresInternet)
    }

    @Test
    fun integerPowerRunsOffline() {
        val solution = assertNotNull(
            UltraMathEngine.solve("Ultra, cuánto es 2 elevado a 8")
        )

        assertEquals("256", solution.resultText)
        assertFalse(solution.requiresInternet)
    }

    @Test
    fun squareRootRunsOffline() {
        val solution = assertNotNull(
            UltraMathEngine.solve("Ultra, raíz cuadrada de 144")
        )

        assertEquals("12", solution.resultText)
        assertFalse(solution.requiresInternet)
    }

    @Test
    fun ruleOfThreeRunsOffline() {
        val solution = assertNotNull(
            UltraMathEngine.solve("Ultra, si 3 cuestan 1200 cuánto cuestan 5")
        )

        assertEquals("2000", solution.resultText)
        assertFalse(solution.requiresInternet)
    }

    @Test
    fun incompleteRuleOfThreeDoesNotInventData() {
        assertNull(
            UltraMathEngine.solve("Ultra, si 3 cuestan 1200 cuánto cuestan")
        )
    }
}
