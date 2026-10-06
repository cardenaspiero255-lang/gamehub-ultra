package com.cardenaspiero255.gamehubultra.ui.home

import com.cardenaspiero255.gamehubultra.domain.OptimizationFeedbackDecision
import com.cardenaspiero255.gamehubultra.domain.OptimizationObservation
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile

internal data class SmartRecommendationRevertTarget(
    val gamePackage: String?,
    val previousProfile: PerformanceProfile,
    val appliedProfile: PerformanceProfile
)

internal object SmartRecommendationRevertPolicy {
    fun capture(
        gamePackage: String?,
        previousProfile: PerformanceProfile,
        appliedProfile: PerformanceProfile
    ): SmartRecommendationRevertTarget =
        SmartRecommendationRevertTarget(
            gamePackage = gamePackage,
            previousProfile = previousProfile,
            appliedProfile = appliedProfile
        )

    fun shouldRecordAccepted(
        currentProfile: PerformanceProfile,
        recommendedProfile: PerformanceProfile
    ): Boolean = currentProfile != recommendedProfile

    fun shouldRecordRejected(alreadyRejected: Boolean): Boolean = !alreadyRejected

    fun rejectionStableObservations(
        recommendedProfile: PerformanceProfile,
        observations: List<OptimizationObservation>
    ): List<OptimizationObservation> =
        observations.filterNot { observation ->
            observation.profile == recommendedProfile &&
                observation.feedbackDecision == OptimizationFeedbackDecision.REJECTED
        }

    fun canRevert(
        target: SmartRecommendationRevertTarget?,
        gamePackage: String?,
        currentProfile: PerformanceProfile
    ): Boolean =
        target != null &&
            target.gamePackage == gamePackage &&
            target.previousProfile != target.appliedProfile &&
            currentProfile == target.appliedProfile
}
