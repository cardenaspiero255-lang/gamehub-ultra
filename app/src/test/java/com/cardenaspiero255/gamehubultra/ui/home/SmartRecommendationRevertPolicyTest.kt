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
            observations = emptyList()
        )
        val after = SmartRecommendationRevertPolicy.rejectionKey(
            contextKey = key,
            profile = PerformanceProfile.X4,
            currentProfile = PerformanceProfile.BALANCED,
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

    @Test
    fun `smart recommendation actions execute feedback and profile effects`() {
        val feedback = mutableListOf<Pair<PerformanceProfile, OptimizationFeedbackDecision>>()
        val selected = mutableListOf<PerformanceProfile>()

        val target = SmartRecommendationActions.apply(
            gamePackage = "game.a",
            currentProfile = PerformanceProfile.BALANCED,
            recommendedProfile = PerformanceProfile.X4,
            recordFeedback = { profile, decision -> feedback += profile to decision },
            selectProfile = selected::add
        )

        assertEquals(PerformanceProfile.BALANCED, target?.previousProfile)
        assertEquals(PerformanceProfile.X4, target?.appliedProfile)
        assertEquals(
            listOf(PerformanceProfile.X4 to OptimizationFeedbackDecision.ACCEPTED),
            feedback
        )
        assertEquals(listOf(PerformanceProfile.X4), selected)

        feedback.clear()
        assertTrue(
            SmartRecommendationActions.reject(
                alreadyRejected = false,
                recommendedProfile = PerformanceProfile.X4,
                recordFeedback = { profile, decision -> feedback += profile to decision }
            )
        )
        assertEquals(
            listOf(PerformanceProfile.X4 to OptimizationFeedbackDecision.REJECTED),
            feedback
        )

        feedback.clear()
        assertTrue(
            SmartRecommendationActions.reject(
                alreadyRejected = true,
                recommendedProfile = PerformanceProfile.X4,
                recordFeedback = { profile, decision -> feedback += profile to decision }
            )
        )
        assertTrue(feedback.isEmpty())

        feedback.clear()
        selected.clear()
        val remainingTarget = SmartRecommendationActions.revert(
            target = target,
            gamePackage = "game.a",
            currentProfile = PerformanceProfile.X4,
            recordFeedback = { profile, decision -> feedback += profile to decision },
            selectProfile = selected::add
        )

        assertEquals(null, remainingTarget)
        assertEquals(
            listOf(PerformanceProfile.X4 to OptimizationFeedbackDecision.REVERTED),
            feedback
        )
        assertEquals(listOf(PerformanceProfile.BALANCED), selected)
    }

    @Test
    fun `smart recommendation actions preserve state when no effect applies`() {
        val feedback = mutableListOf<Pair<PerformanceProfile, OptimizationFeedbackDecision>>()
        val selected = mutableListOf<PerformanceProfile>()

        val noChangeTarget = SmartRecommendationActions.apply(
            gamePackage = "game.a",
            currentProfile = PerformanceProfile.X4,
            recommendedProfile = PerformanceProfile.X4,
            recordFeedback = { profile, decision -> feedback += profile to decision },
            selectProfile = selected::add
        )
        assertEquals(null, noChangeTarget)
        assertTrue(feedback.isEmpty())
        assertEquals(listOf(PerformanceProfile.X4), selected)

        val target = SmartRecommendationRevertPolicy.capture(
            gamePackage = "game.a",
            previousProfile = PerformanceProfile.BALANCED,
            appliedProfile = PerformanceProfile.X4
        )
        feedback.clear()
        selected.clear()

        val preserved = SmartRecommendationActions.revert(
            target = target,
            gamePackage = "game.b",
            currentProfile = PerformanceProfile.X4,
            recordFeedback = { profile, decision -> feedback += profile to decision },
            selectProfile = selected::add
        )

        assertEquals(target, preserved)
        assertTrue(feedback.isEmpty())
        assertTrue(selected.isEmpty())
    }

    @Test
    fun `repeated apply preserves existing revert target`() {
        val feedback = mutableListOf<Pair<PerformanceProfile, OptimizationFeedbackDecision>>()
        val selected = mutableListOf<PerformanceProfile>()
        val existingTarget = SmartRecommendationRevertPolicy.capture(
            gamePackage = "game.a",
            previousProfile = PerformanceProfile.BALANCED,
            appliedProfile = PerformanceProfile.X4
        )

        val preserved = SmartRecommendationActions.apply(
            gamePackage = "game.a",
            currentProfile = PerformanceProfile.X4,
            recommendedProfile = PerformanceProfile.X4,
            recordFeedback = { profile, decision -> feedback += profile to decision },
            selectProfile = selected::add,
            existingRevertTarget = existingTarget
        )

        assertEquals(existingTarget, preserved)
        assertTrue(feedback.isEmpty())
        assertEquals(listOf(PerformanceProfile.X4), selected)
    }

}
