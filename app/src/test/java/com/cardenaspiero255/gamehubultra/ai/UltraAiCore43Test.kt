package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UltraAiCore43Test {
    @Test
    fun `one rejection lowers confidence without immediately replacing the suggestion`() {
        val result = UltraAiCore2().evaluate(
            observation = UltraAiObservation(
                gamePackage = "com.example.game",
                activeProfileId = "X4",
                batteryPercent = 80,
                thermalLabel = "normal"
            ),
            feedback = UltraAiFeedbackSnapshot(
                rejectedProfileIds = setOf("X4"),
                rejectedProfileCounts = mapOf("X4" to 1)
            ),
            memories = emptyList()
        )

        assertEquals("X4", result.recommendation.profileId)
        assertTrue(result.recommendation.confidence < 0.72)
        assertTrue(result.recommendation.evidence.contains("recovery=confidence-reduced"))
        assertTrue(result.explanation.contains("ajusté", ignoreCase = true))
    }

    @Test
    fun `repeated poor suggestions recover to a known accepted profile`() {
        val result = UltraAiCore2(
            recommender = UltraAiRecommender { _, _, _ ->
                UltraAiRecommendation(
                    profileId = "X4",
                    confidence = 0.92,
                    evidence = listOf("model=x4")
                )
            }
        ).evaluate(
            observation = UltraAiObservation(
                gamePackage = "com.example.game",
                activeProfileId = "X4",
                batteryPercent = 80,
                thermalLabel = "normal"
            ),
            feedback = UltraAiFeedbackSnapshot(
                acceptedProfileIds = setOf("BALANCED"),
                rejectedProfileIds = setOf("X4"),
                rejectedProfileCounts = mapOf("X4" to 3)
            ),
            memories = emptyList()
        )

        assertEquals("BALANCED", result.recommendation.profileId)
        assertTrue(result.recommendation.confidence < 0.92)
        assertTrue(result.recommendation.evidence.contains("recovery=poor-history"))
        assertTrue(result.explanation.contains("rendimiento previo", ignoreCase = true))
    }

    @Test
    fun `reverted recommendations count as poor outcomes and trigger recovery`() {
        val result = UltraAiCore2(
            recommender = UltraAiRecommender { _, _, _ ->
                UltraAiRecommendation("FRAME_INTERPOLATION", 0.86, listOf("model=fi"))
            }
        ).evaluate(
            observation = UltraAiObservation(
                gamePackage = "com.example.game",
                activeProfileId = "BALANCED",
                batteryPercent = 75,
                thermalLabel = "normal"
            ),
            feedback = UltraAiFeedbackSnapshot(
                acceptedProfileIds = setOf("BALANCED"),
                revertedProfileIds = setOf("FRAME_INTERPOLATION"),
                revertedProfileCounts = mapOf("FRAME_INTERPOLATION" to 2)
            ),
            memories = emptyList()
        )

        assertEquals("BALANCED", result.recommendation.profileId)
        assertTrue(result.recommendation.evidence.contains("feedback=reverted"))
    }

    @Test
    fun `contradictory feedback is detected instead of silently trusting either side`() {
        val result = UltraAiCore2().evaluate(
            observation = UltraAiObservation(
                gamePackage = "com.example.game",
                activeProfileId = "BALANCED",
                batteryPercent = 70,
                thermalLabel = "normal"
            ),
            feedback = UltraAiFeedbackSnapshot(
                acceptedProfileIds = setOf("BALANCED"),
                rejectedProfileIds = setOf("BALANCED")
            ),
            memories = emptyList()
        )

        assertTrue(result.recommendation.evidence.contains("feedback=contradiction"))
        assertTrue(result.recommendation.confidence < 0.72)
    }
}
