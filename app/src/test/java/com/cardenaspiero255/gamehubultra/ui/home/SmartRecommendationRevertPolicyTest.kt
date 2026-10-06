package com.cardenaspiero255.gamehubultra.ui.home

import com.cardenaspiero255.gamehubultra.data.OptimizationContextKey
import com.cardenaspiero255.gamehubultra.domain.OptimizationFeedbackDecision
import com.cardenaspiero255.gamehubultra.domain.OptimizationObservation
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SmartRecommendationRevertPolicyTest {
    @Test
    fun `revert target preserves the profile that existed before apply`() {
        val target = SmartRecommendationRevertPolicy.capture(
            gamePackage = "game.a",
            previousProfile = PerformanceProfile.FRAME_INTERPOLATION,
            appliedProfile = PerformanceProfile.X4
        )

        assertEquals(PerformanceProfile.FRAME_INTERPOLATION, target.previousProfile)
        assertTrue(
            SmartRecommendationRevertPolicy.canRevert(
                target = target,
                gamePackage = "game.a",
                currentProfile = PerformanceProfile.X4
            )
        )
        assertFalse(
            SmartRecommendationRevertPolicy.canRevert(
                target = target,
                gamePackage = "game.a",
                currentProfile = PerformanceProfile.BALANCED
            )
        )
        assertFalse(
            SmartRecommendationRevertPolicy.canRevert(
                target = target,
                gamePackage = "game.b",
                currentProfile = PerformanceProfile.X4
            )
        )
    }

    @Test
    fun `applying an already active recommendation does not create duplicate accepted feedback`() {
        assertFalse(
            SmartRecommendationRevertPolicy.shouldRecordAccepted(
                currentProfile = PerformanceProfile.X4,
                recommendedProfile = PerformanceProfile.X4
            )
        )
        assertTrue(
            SmartRecommendationRevertPolicy.shouldRecordAccepted(
                currentProfile = PerformanceProfile.BALANCED,
                recommendedProfile = PerformanceProfile.X4
            )
        )
    }
    @Test
    fun `same contextual recommendation records at most one rejection`() {
        assertTrue(SmartRecommendationRevertPolicy.shouldRecordRejected(alreadyRejected = false))
        assertFalse(SmartRecommendationRevertPolicy.shouldRecordRejected(alreadyRejected = true))
    }


    @Test
    fun `rejection key ignores only rejected evidence for displayed profile`() {
        val ownRejected = OptimizationObservation(
            contextKey = "ctx",
            profile = PerformanceProfile.X4,
            timestampMillis = 1L,
            feedbackDecision = OptimizationFeedbackDecision.REJECTED
        )
        val ownAccepted = OptimizationObservation(
            contextKey = "ctx",
            profile = PerformanceProfile.X4,
            timestampMillis = 2L,
            feedbackDecision = OptimizationFeedbackDecision.ACCEPTED
        )
        val otherRejected = OptimizationObservation(
            contextKey = "ctx",
            profile = PerformanceProfile.BALANCED,
            timestampMillis = 3L,
            feedbackDecision = OptimizationFeedbackDecision.REJECTED
        )

        val stable = SmartRecommendationRevertPolicy.rejectionStableObservations(
            recommendedProfile = PerformanceProfile.X4,
            observations = listOf(ownRejected, ownAccepted, otherRejected)
        )

        assertEquals(listOf(ownAccepted, otherRejected), stable)
    }


    @Test
    fun `rejection key is stable when only own rejected evidence changes`() {
        val key = OptimizationContextKey("device", "game.a", "1", "native", "gpu")
        val rejected = OptimizationObservation(
            contextKey = key.serialized,
            profile = PerformanceProfile.X4,
            timestampMillis = 1L,
            feedbackDecision = OptimizationFeedbackDecision.REJECTED
        )

        val before = SmartRecommendationRevertPolicy.rejectionKey(
            contextKey = key,
            profile = PerformanceProfile.X4,
            currentProfile = PerformanceProfile.BALANCED,
            runtime = null,
            observations = emptyList()
        )
        val after = SmartRecommendationRevertPolicy.rejectionKey(
            contextKey = key,
            profile = PerformanceProfile.X4,
            currentProfile = PerformanceProfile.BALANCED,
            runtime = null,
            observations = listOf(rejected)
        )

        assertEquals(before, after)
    }

    @Test
    fun `apply decision records accepted only when recommendation changes profile`() {
        val changed = SmartRecommendationRevertPolicy.applyDecision(
            gamePackage = "game.a",
            currentProfile = PerformanceProfile.BALANCED,
            recommendedProfile = PerformanceProfile.X4
        )
        assertEquals(OptimizationFeedbackDecision.ACCEPTED, changed.feedbackDecision)
        assertEquals(PerformanceProfile.BALANCED, changed.revertTarget?.previousProfile)
        assertEquals(PerformanceProfile.X4, changed.revertTarget?.appliedProfile)

        val unchanged = SmartRecommendationRevertPolicy.applyDecision(
            gamePackage = "game.a",
            currentProfile = PerformanceProfile.X4,
            recommendedProfile = PerformanceProfile.X4
        )
        assertEquals(null, unchanged.feedbackDecision)
        assertEquals(null, unchanged.revertTarget)
    }

    @Test
    fun `revert decision restores only the captured applied recommendation`() {
        val target = SmartRecommendationRevertPolicy.capture(
            gamePackage = "game.a",
            previousProfile = PerformanceProfile.BALANCED,
            appliedProfile = PerformanceProfile.X4
        )

        val decision = SmartRecommendationRevertPolicy.revertDecision(
            target = target,
            gamePackage = "game.a",
            currentProfile = PerformanceProfile.X4
        )
        assertEquals(PerformanceProfile.X4, decision?.feedbackProfile)
        assertEquals(PerformanceProfile.BALANCED, decision?.restoreProfile)

        assertEquals(
            null,
            SmartRecommendationRevertPolicy.revertDecision(
                target = target,
                gamePackage = "game.b",
                currentProfile = PerformanceProfile.X4
            )
        )
    }

}
