package com.cardenaspiero255.gamehubultra.ai

object AiActionAllowlist {
    const val PROFILE_BALANCED = "PROFILE_BALANCED"
    const val PROFILE_INTERPOLATION = "PROFILE_INTERPOLATION"
    const val PROFILE_X4 = "PROFILE_X4"
    const val ADVICE = "ADVICE"

    private val allowed = setOf(
        PROFILE_BALANCED,
        PROFILE_INTERPOLATION,
        PROFILE_X4,
        ADVICE
    )

    fun validate(candidate: LocalAiActionCandidate): Boolean {
        if (candidate.action !in allowed) return false

        val argument = candidate.argument?.trim()
        return when (candidate.action) {
            PROFILE_BALANCED,
            PROFILE_INTERPOLATION,
            PROFILE_X4,
            ADVICE -> argument.isNullOrEmpty()
            else -> false
        }
    }
}
