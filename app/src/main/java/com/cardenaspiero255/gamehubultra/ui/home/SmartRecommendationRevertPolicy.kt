package com.cardenaspiero255.gamehubultra.ui.home

import com.cardenaspiero255.gamehubultra.data.OptimizationContextKey
import com.cardenaspiero255.gamehubultra.domain.OptimizationFeedbackDecision
import com.cardenaspiero255.gamehubultra.domain.OptimizationObservation
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile

internal data class SmartRecommendationRevertTarget(
    val gamePackage: String?,
    val previousProfile: PerformanceProfile,
    val appliedProfile: PerformanceProfile
)

internal data class SmartRecommendationRejectionKey(
    val contextKey: OptimizationContextKey,
    val profile: PerformanceProfile,
    val currentProfile: PerformanceProfile,
    val stableObservations: List<OptimizationObservation>
)

internal data class SmartRecommendationApplyDecision(
    val revertTarget: SmartRecommendationRevertTarget?,
    val feedbackDecision: OptimizationFeedbackDecision?
)

internal data class SmartRecommendationRevertDecision(
    val feedbackProfile: PerformanceProfile,
    val restoreProfile: PerformanceProfile
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

    fun rejectionKey(
        contextKey: OptimizationContextKey,
        profile: PerformanceProfile,
        currentProfile: PerformanceProfile,
        observations: List<OptimizationObservation>
    ): SmartRecommendationRejectionKey =
        SmartRecommendationRejectionKey(
            contextKey = contextKey,
            profile = profile,
            currentProfile = currentProfile,
            stableObservations = rejectionStableObservations(profile, observations)
        )

    fun applyDecision(
        gamePackage: String?,
        currentProfile: PerformanceProfile,
        recommendedProfile: PerformanceProfile
    ): SmartRecommendationApplyDecision =
        if (shouldRecordAccepted(currentProfile, recommendedProfile)) {
            SmartRecommendationApplyDecision(
                revertTarget = capture(gamePackage, currentProfile, recommendedProfile),
                feedbackDecision = OptimizationFeedbackDecision.ACCEPTED
            )
        } else {
            SmartRecommendationApplyDecision(
                revertTarget = null,
                feedbackDecision = null
            )
        }

    fun revertDecision(
        target: SmartRecommendationRevertTarget?,
        gamePackage: String?,
        currentProfile: PerformanceProfile
    ): SmartRecommendationRevertDecision? =
        target
            ?.takeIf { canRevert(it, gamePackage, currentProfile) }
            ?.let {
                SmartRecommendationRevertDecision(
                    feedbackProfile = it.appliedProfile,
                    restoreProfile = it.previousProfile
                )
            }

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
