package com.cardenaspiero255.gamehubultra

import com.cardenaspiero255.gamehubultra.ai.AiAdviceReason
import com.cardenaspiero255.gamehubultra.ai.GameHubAiAdvice
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.voice.VoiceActionResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UltraCommandUiEffectPolicyTest {

    @Test
    fun profileSelectionAppliesThroughCurrentGameCallback() {
        val result = VoiceActionResult.ProfileSelected(
            profile = PerformanceProfile.X4,
            deferred = false
        )

        assertEquals(
            PerformanceProfile.X4,
            UltraCommandUiEffectPolicy.profileForCurrentGameCallback(result)
        )
    }

    @Test
    fun openedGameDoesNotReapplyItsProfileThroughCurrentGameCallback() {
        val result = VoiceActionResult.GameOpened(
            game = GameInfo("minecraft", "Minecraft"),
            profile = PerformanceProfile.X4,
            profileUnavailable = false,
            profileDeferred = false
        )

        assertNull(UltraCommandUiEffectPolicy.profileForCurrentGameCallback(result))
    }

    @Test
    fun adviceRecommendationCannotApplyToDifferentSelectedGame() {
        val result = VoiceActionResult.AiAdvice(
            GameHubAiAdvice(
                readiness = 92,
                suggestedProfile = PerformanceProfile.X4,
                reason = AiAdviceReason.X4_READY,
                localModelUsed = false,
                fallbackUsed = true
            )
        )

        val recommendation =
            UltraCommandUiEffectPolicy.recommendationForUserApply(
                result = result,
                gamePackage = "game.a"
            )

        assertEquals(
            PerformanceProfile.X4,
            UltraCommandUiEffectPolicy.profileForCurrentGame(
                recommendation = recommendation,
                currentGamePackage = "game.a"
            )
        )
        assertNull(
            UltraCommandUiEffectPolicy.profileForCurrentGame(
                recommendation = recommendation,
                currentGamePackage = "game.b"
            )
        )
    }

    @Test
    fun aiAdviceExposesSuggestedProfileForExplicitUserApply() {
        val result = VoiceActionResult.AiAdvice(
            GameHubAiAdvice(
                readiness = 92,
                suggestedProfile = PerformanceProfile.X4,
                reason = AiAdviceReason.X4_READY,
                localModelUsed = false,
                fallbackUsed = true
            )
        )

        assertEquals(
            PerformanceProfile.X4,
            UltraCommandUiEffectPolicy.recommendedProfileForUserApply(result)
        )
    }

}
