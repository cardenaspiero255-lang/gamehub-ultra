package com.cardenaspiero255.gamehubultra

import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.voice.VoiceActionResult

internal data class UltraScopedProfileRecommendation(
    val gamePackage: String?,
    val profile: PerformanceProfile
)

/**
 * Keeps command-result UI callbacks from duplicating persistence already
 * performed by VoiceCommandEngine.
 */
internal object UltraCommandUiEffectPolicy {
    fun profileForCurrentGameCallback(
        result: VoiceActionResult
    ): PerformanceProfile? =
        when (result) {
            is VoiceActionResult.ProfileSelected -> result.profile
            else -> null
        }

    fun recommendedProfileForUserApply(
        result: VoiceActionResult
    ): PerformanceProfile? =
        (result as? VoiceActionResult.AiAdvice)?.advice?.suggestedProfile

    fun recommendationForUserApply(
        result: VoiceActionResult,
        gamePackage: String?
    ): UltraScopedProfileRecommendation? =
        recommendedProfileForUserApply(result)?.let { profile ->
            UltraScopedProfileRecommendation(
                gamePackage = gamePackage,
                profile = profile
            )
        }

    fun profileForCurrentGame(
        recommendation: UltraScopedProfileRecommendation?,
        currentGamePackage: String?
    ): PerformanceProfile? =
        recommendation
            ?.takeIf { it.gamePackage == currentGamePackage }
            ?.profile
}
