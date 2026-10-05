package com.cardenaspiero255.gamehubultra.ui.home

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

}
