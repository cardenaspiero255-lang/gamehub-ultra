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

    @Test
    fun `plural Spanish magnitudes work across arithmetic operators`() {
        assertEquals("6000000", UltraMathEngine.solve("2 millones por 3")?.resultText)
        assertEquals("5020", UltraMathEngine.solve("5 mil + 20")?.resultText)
        assertEquals("999000", UltraMathEngine.solve("1 millón - 1 mil")?.resultText)
    }

    @Test
    fun `common English magnitudes are understood offline too`() {
        assertEquals("2000000", UltraMathEngine.solve("one million x 2")?.resultText)
        assertEquals("2000000000", UltraMathEngine.solve("1 billion x 2")?.resultText)
    }
    @Test
    fun `large integer arithmetic does not lose precision`() {
        val solution = UltraMathEngine.solve("999999999999 x 999999999999")

        assertEquals("999999999998000000000001", solution?.resultText)
    }

}
