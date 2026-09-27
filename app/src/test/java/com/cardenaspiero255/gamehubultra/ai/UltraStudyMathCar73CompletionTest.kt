package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class UltraStudyMathCar73CompletionTest {

    @Test
    fun twoVariableLinearSystemRunsOffline() {
        val solution = assertNotNull(
            UltraMathEngine.solve(
                "Ultra, resuelve el sistema x + y = 10 y x - y = 2"
            )
        )

        assertEquals("x = 6, y = 4", solution.resultText)
        assertFalse(solution.requiresInternet)
    }

    @Test
    fun rectangleAreaRunsOfflineWithUnits() {
        val solution = assertNotNull(
            UltraMathEngine.solve(
                "Ultra, área de un rectángulo de 5 metros por 8 metros"
            )
        )

        assertEquals("40 m²", solution.resultText)
    }

    @Test
    fun rectanglePerimeterRunsOfflineWithUnits() {
        val solution = assertNotNull(
            UltraMathEngine.solve(
                "Ultra, perímetro de un rectángulo de 5 metros por 8 metros"
            )
        )

        assertEquals("26 m", solution.resultText)
    }

    @Test
    fun rectangularPrismVolumeRunsOffline() {
        val solution = assertNotNull(
            UltraMathEngine.solve(
                "Ultra, volumen de un prisma rectangular de 3 por 4 por 5 metros"
            )
        )

        assertEquals("60 m³", solution.resultText)
    }

    @Test
    fun sineInDegreesIsDeterministic() {
        val solution = assertNotNull(
            UltraMathEngine.solve("Ultra, seno de 30 grados")
        )

        assertEquals("0.5", solution.resultText)
    }

    @Test
    fun sineInRadiansUsesRadiansInsteadOfDegrees() {
        val solution = assertNotNull(
            UltraMathEngine.solve(
                "Ultra, seno de 0.5235987755982988 radianes"
            )
        )

        assertEquals("0.5", solution.resultText)
    }

    @Test
    fun linearFunctionEvaluationRunsOffline() {
        val solution = assertNotNull(
            UltraMathEngine.solve(
                "Ultra, función f de x = 2x + 3, evalúa en x = 4"
            )
        )

        assertEquals("f(4) = 11", solution.resultText)
    }

    @Test
    fun arithmeticMeanRunsOffline() {
        val solution = assertNotNull(
            UltraMathEngine.solve(
                "Ultra, promedio de 2, 4, 6 y 8"
            )
        )

        assertEquals("5", solution.resultText)
    }

    @Test
    fun medianRunsOffline() {
        val solution = assertNotNull(
            UltraMathEngine.solve(
                "Ultra, mediana de 9, 1, 5 y 3"
            )
        )

        assertEquals("4", solution.resultText)
    }
}
