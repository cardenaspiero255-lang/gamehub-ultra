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

    @Test
    fun `rejection never increases an already low confidence`() {
        val result = UltraAiCore2(
            recommender = UltraAiRecommender { _, _, _ ->
                UltraAiRecommendation(
                    profileId = "X4",
                    confidence = 0.04,
                    evidence = listOf("model=low-confidence")
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
                rejectedProfileIds = setOf("X4")
            ),
            memories = emptyList()
        )

        assertTrue(result.recommendation.confidence <= 0.04)
    }


    @Test
    fun `repeated poor suggestion keeps a safe active alternative before defaulting balanced`() {
        val result = UltraAiCore2(
            recommender = UltraAiRecommender { _, _, _ ->
                UltraAiRecommendation("X4", 0.90, listOf("model=x4"))
            }
        ).evaluate(
            observation = UltraAiObservation(
                gamePackage = "com.example.game",
                activeProfileId = "FRAME_INTERPOLATION",
                batteryPercent = 80,
                thermalLabel = "normal"
            ),
            feedback = UltraAiFeedbackSnapshot(
                rejectedProfileIds = setOf("X4"),
                rejectedProfileCounts = mapOf("X4" to 2)
            ),
            memories = emptyList()
        )

        assertEquals("FRAME_INTERPOLATION", result.recommendation.profileId)
    }

    @Test
    fun `repeated poor suggestion falls back to balanced when active profile is also poor suggestion`() {
        val result = UltraAiCore2(
            recommender = UltraAiRecommender { _, _, _ ->
                UltraAiRecommendation("X4", 0.90, listOf("model=x4"))
            }
        ).evaluate(
            observation = UltraAiObservation(
                gamePackage = "com.example.game",
                activeProfileId = "X4",
                batteryPercent = 80,
                thermalLabel = "normal"
            ),
            feedback = UltraAiFeedbackSnapshot(
                rejectedProfileIds = setOf("X4"),
                rejectedProfileCounts = mapOf("X4" to 2)
            ),
            memories = emptyList()
        )

        assertEquals("BALANCED", result.recommendation.profileId)
    }

    @Test
    fun `balanced stays balanced when every safe fallback is already poor`() {
        val result = UltraAiCore2(
            recommender = UltraAiRecommender { _, _, _ ->
                UltraAiRecommendation("BALANCED", 0.80, listOf("model=balanced"))
            }
        ).evaluate(
            observation = UltraAiObservation(
                gamePackage = "com.example.game",
                activeProfileId = "BALANCED",
                batteryPercent = 80,
                thermalLabel = "normal"
            ),
            feedback = UltraAiFeedbackSnapshot(
                rejectedProfileIds = setOf("BALANCED"),
                rejectedProfileCounts = mapOf("BALANCED" to 2)
            ),
            memories = emptyList()
        )

        assertEquals("BALANCED", result.recommendation.profileId)
    }

}
