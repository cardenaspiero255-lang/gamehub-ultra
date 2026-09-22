package com.cardenaspiero255.gamehubultra.ai

import android.content.Context
import com.cardenaspiero255.gamehubultra.R

object AiAdviceFormatter {
    fun title(context: Context, advice: GameHubAiAdvice): String =
        context.getString(profileTitleRes(advice.suggestedProfile))

    fun explanation(context: Context, advice: GameHubAiAdvice): String =
        context.getString(
            R.string.ai_reason_with_readiness,
            advice.readiness,
            context.getString(reasonRes(advice.reason))
        )

    fun fullResponse(context: Context, advice: GameHubAiAdvice): String =
        title(context, advice) + ". " + explanation(context, advice)

    private fun profileTitleRes(profile: com.cardenaspiero255.gamehubultra.domain.PerformanceProfile): Int =
        when (profile) {
            com.cardenaspiero255.gamehubultra.domain.PerformanceProfile.BALANCED ->
                R.string.ai_profile_balanced_title
            com.cardenaspiero255.gamehubultra.domain.PerformanceProfile.FRAME_INTERPOLATION ->
                R.string.ai_profile_interpolation_title
            com.cardenaspiero255.gamehubultra.domain.PerformanceProfile.X4 ->
                R.string.ai_profile_x4_title
        }

    private fun reasonRes(reason: AiAdviceReason): Int =
        when (reason) {
            AiAdviceReason.THERMAL -> R.string.ai_reason_thermal
            AiAdviceReason.LOW_BATTERY -> R.string.ai_reason_battery
            AiAdviceReason.LOW_STORAGE -> R.string.ai_reason_storage
            AiAdviceReason.NETWORK -> R.string.ai_reason_network
            AiAdviceReason.X4_READY -> R.string.ai_reason_x4_ready
            AiAdviceReason.INTERPOLATION -> R.string.ai_reason_interpolation
            AiAdviceReason.BALANCED_GENERAL -> R.string.ai_reason_balanced
            AiAdviceReason.LOCAL_MODEL_BALANCED -> R.string.ai_reason_model_balanced
            AiAdviceReason.LOCAL_MODEL_INTERPOLATION -> R.string.ai_reason_model_interpolation
            AiAdviceReason.LOCAL_MODEL_X4 -> R.string.ai_reason_model_x4
        }
}
