package com.cardenaspiero255.gamehubultra.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class UltraMathMagnitudeRegressionTest {
    @Test
    fun `one million times five hundred is solved offline`() {
        val solution = UltraMathEngine.solve("1 millón X 500")
        assertNotNull(solution)
        assertEquals("500000000", solution?.resultText)
        assertEquals(false, solution?.requiresInternet)
    }
}
