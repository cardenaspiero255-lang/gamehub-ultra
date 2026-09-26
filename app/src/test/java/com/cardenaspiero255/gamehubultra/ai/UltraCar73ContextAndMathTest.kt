package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UltraCar73ContextAndMathTest {
    @Test
    fun followUpReusesLastComparedEntities() {
        val context = UltraConversationContext()
        context.observe("Ultra, compara el RedMagic 11S Pro con el Galaxy S26 Ultra")

        val resolved = context.resolveFollowUp("¿y cuál tiene mejor batería?")

        assertTrue(resolved.contains("RedMagic 11S Pro"))
        assertTrue(resolved.contains("Galaxy S26 Ultra"))
        assertTrue(resolved.contains("mejor batería"))
    }

    @Test
    fun explicitNewTopicReplacesPreviousEntities() {
        val context = UltraConversationContext()
        context.observe("Ultra, compara el RedMagic 11S Pro con el Galaxy S26 Ultra")
        context.observe("Ahora háblame del Pixel 11 Pro")

        val resolved = context.resolveFollowUp("¿y su batería?")

        assertTrue(resolved.contains("Pixel 11 Pro"))
        assertFalse(resolved.contains("RedMagic 11S Pro"))
    }

    @Test
    fun percentageUsesOfflineDeterministicMath() {
        val solution = assertNotNull(
            UltraMathEngine.solve("Ultra, cuánto es el 15 por ciento de 240")
        )

        assertEquals("36", solution.resultText)
        assertFalse(solution.requiresInternet)
    }

    @Test
    fun unitConversionUsesOfflineDeterministicMath() {
        val solution = assertNotNull(
            UltraMathEngine.solve("Ultra, convierte 2.5 kilómetros a metros")
        )

        assertEquals("2500 m", solution.resultText)
        assertFalse(solution.requiresInternet)
    }

    @Test
    fun incompleteTriangleDoesNotInventMissingData() {
        val solution = UltraMathEngine.solve(
            "Ultra, tengo un triángulo con un ángulo de 35 grados, cuánto mide el tercero"
        )

        assertEquals(null, solution)
    }
}
