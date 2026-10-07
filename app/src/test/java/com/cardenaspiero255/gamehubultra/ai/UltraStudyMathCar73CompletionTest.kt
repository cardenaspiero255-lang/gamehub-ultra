package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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
    @Test
    fun quadraticEquationWithTwoRealRootsRunsOffline() {
        val solution = assertNotNull(
            UltraMathEngine.solve("Ultra, resuelve x^2 - 5x + 6 = 0")
        )

        assertEquals("x = 2 o x = 3", solution.resultText)
        assertFalse(solution.requiresInternet)
    }

    @Test
    fun greatestCommonDivisorRunsOffline() {
        val solution = assertNotNull(
            UltraMathEngine.solve("Ultra, calcula el MCD de 48 y 18")
        )

        assertEquals("6", solution.resultText)
    }

    @Test
    fun leastCommonMultipleRunsOffline() {
        val solution = assertNotNull(
            UltraMathEngine.solve("Ultra, calcula el MCM de 48 y 18")
        )

        assertEquals("144", solution.resultText)
    }

    @Test
    fun pythagoreanHypotenuseRunsOffline() {
        val solution = assertNotNull(
            UltraMathEngine.solve(
                "Ultra, por Pitágoras calcula la hipotenusa con catetos 3 y 4"
            )
        )

        assertEquals("5", solution.resultText)
    }

    @Test
    fun celsiusToFahrenheitRunsOffline() {
        val solution = assertNotNull(
            UltraMathEngine.solve("Ultra, convierte 25 grados Celsius a Fahrenheit")
        )

        assertEquals("77 °F", solution.resultText)
    }

    @Test
    fun quadraticEquationWithDoubleRootRunsOffline() {
        val solution = assertNotNull(
            UltraMathEngine.solve("Ultra, resuelve x^2 - 2x + 1 = 0")
        )

        assertEquals("x = 1", solution.resultText)
    }

    @Test
    fun quadraticEquationWithoutRealRootsDoesNotInventARealAnswer() {
        assertNull(
            UltraMathEngine.solve("Ultra, resuelve x^2 + x + 1 = 0")
        )
    }

    @Test
    fun leastCommonMultipleWithZeroIsZero() {
        val solution = assertNotNull(
            UltraMathEngine.solve("Ultra, calcula el MCM de 0 y 18")
        )

        assertEquals("0", solution.resultText)
    }

    @Test
    fun fahrenheitToCelsiusRunsOffline() {
        val solution = assertNotNull(
            UltraMathEngine.solve("Ultra, convierte 32 Fahrenheit a Celsius")
        )

        assertEquals("0 °C", solution.resultText)
    }

    @Test
    fun celsiusToKelvinRunsOffline() {
        val solution = assertNotNull(
            UltraMathEngine.solve("Ultra, convierte 0 Celsius a Kelvin")
        )

        assertEquals("273.15 K", solution.resultText)
    }

    @Test
    fun kelvinToCelsiusRunsOffline() {
        val solution = assertNotNull(
            UltraMathEngine.solve("Ultra, convierte 273.15 Kelvin a Celsius")
        )

        assertEquals("0 °C", solution.resultText)
    }

    @Test
    fun temperaturesBelowAbsoluteZeroAreRejected() {
        assertNull(
            UltraMathEngine.solve("Ultra, convierte -1 Kelvin a Celsius")
        )
    }

}
