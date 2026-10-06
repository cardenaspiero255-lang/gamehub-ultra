package com.cardenaspiero255.gamehubultra.ui.home

import com.cardenaspiero255.gamehubultra.domain.OptimizationFeedbackDecision
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile

internal object SmartRecommendationActions {
    fun apply(
        gamePackage: String?,
        currentProfile: PerformanceProfile,
        recommendedProfile: PerformanceProfile,
        recordFeedback: (PerformanceProfile, OptimizationFeedbackDecision) -> Unit,
        selectProfile: (PerformanceProfile) -> Unit,
        existingRevertTarget: SmartRecommendationRevertTarget? = null
    ): SmartRecommendationRevertTarget? {
        val decision = SmartRecommendationRevertPolicy.applyDecision(
            gamePackage = gamePackage,
            currentProfile = currentProfile,
            recommendedProfile = recommendedProfile
        )
        decision.feedbackDecision?.let { feedback ->
            recordFeedback(recommendedProfile, feedback)
        }
        selectProfile(recommendedProfile)
        return decision.revertTarget ?: existingRevertTarget?.takeIf {
            SmartRecommendationRevertPolicy.canRevert(
                target = it,
                gamePackage = gamePackage,
                currentProfile = currentProfile
            )
        }
    }


    fun onProfileChanged(
        target: SmartRecommendationRevertTarget?,
        gamePackage: String?,
        currentProfile: PerformanceProfile
    ): SmartRecommendationRevertTarget? =
        target?.takeIf {
            SmartRecommendationRevertPolicy.canRevert(
                target = it,
                gamePackage = gamePackage,
                currentProfile = currentProfile
            )
        }

    fun reject(
        alreadyRejected: Boolean,
        recommendedProfile: PerformanceProfile,
        recordFeedback: (PerformanceProfile, OptimizationFeedbackDecision) -> Unit
    ): Boolean {
        if (!SmartRecommendationRevertPolicy.shouldRecordRejected(alreadyRejected)) {
            return alreadyRejected
        }
        recordFeedback(
            recommendedProfile,
            OptimizationFeedbackDecision.REJECTED
        )
        return true
    }

    fun revert(
        target: SmartRecommendationRevertTarget?,
        gamePackage: String?,
        currentProfile: PerformanceProfile,
        recordFeedback: (PerformanceProfile, OptimizationFeedbackDecision) -> Unit,
        selectProfile: (PerformanceProfile) -> Unit
    ): SmartRecommendationRevertTarget? {
        val decision = SmartRecommendationRevertPolicy.revertDecision(
            target = target,
            gamePackage = gamePackage,
            currentProfile = currentProfile
        ) ?: return target

        recordFeedback(
            decision.feedbackProfile,
            OptimizationFeedbackDecision.REVERTED
        )
        selectProfile(decision.restoreProfile)
        return null
    }
}
