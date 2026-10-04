package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UltraAiCore41Test {
    @Test
    fun `core separates observation recommendation explanation feedback and memory layers`() {
        val core = UltraAiCore2(
            recommender = UltraAiRecommender { observation, _ ->
                UltraAiRecommendation(
                    profileId = "BALANCED",
                    confidence = 0.80,
                    evidence = listOf("battery=" + observation.batteryPercent)
                )
            }
        )

        val result = core.evaluate(
            observation = UltraAiObservation(
                gamePackage = "com.example.game",
                activeProfileId = "X4",
                batteryPercent = 18,
                thermalLabel = "moderado",
                refreshRateHz = 120f,
                latencyMs = 42
            ),
            feedback = UltraAiFeedbackSnapshot(),
            memories = listOf(
                UltraAiMemorySignal(
                    text = "Prefiero estabilidad",
                    provenance = UltraMemoryProvenance.REMEMBERED_FACT
                )
            )
        )

        assertEquals("BALANCED", result.recommendation.profileId)
        assertTrue(result.explanation.contains("battery=18"))
        assertEquals(1, result.memorySignals.size)
    }

    @Test
    fun `core uses deterministic local fallback without model or cloud`() {
        val result = UltraAiCore2(recommender = null).evaluate(
            observation = UltraAiObservation(
                gamePackage = "com.example.game",
                activeProfileId = "X4",
                batteryPercent = 12,
                thermalLabel = "alto",
                refreshRateHz = 120f,
                latencyMs = 50
            ),
            feedback = UltraAiFeedbackSnapshot(),
            memories = emptyList()
        )

        assertEquals(UltraAiRecommendationSource.DETERMINISTIC_LOCAL, result.recommendation.source)
        assertFalse(result.requiresCloud)
        assertTrue(result.recommendation.evidence.isNotEmpty())
    }

    @Test
    fun `fallback never invents unavailable diagnostics`() {
        val result = UltraAiCore2(recommender = null).evaluate(
            observation = UltraAiObservation(
                gamePackage = "com.example.game",
                activeProfileId = "BALANCED"
            ),
            feedback = UltraAiFeedbackSnapshot(),
            memories = emptyList()
        )

        assertFalse(result.explanation.contains("fps", ignoreCase = true))
        assertFalse(result.explanation.contains("temperatura:", ignoreCase = true))
        assertFalse(result.explanation.contains("latencia:", ignoreCase = true))
    }
}
