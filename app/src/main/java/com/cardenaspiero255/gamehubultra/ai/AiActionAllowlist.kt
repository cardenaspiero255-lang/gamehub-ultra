package com.cardenaspiero255.gamehubultra.ai

object AiActionAllowlist {
    const val PROFILE_BALANCED = "PROFILE_BALANCED"
    const val PROFILE_INTERPOLATION = "PROFILE_INTERPOLATION"
    const val PROFILE_X4 = "PROFILE_X4"
    const val OPEN_INSTALLED_GAME = "OPEN_INSTALLED_GAME"
    const val DEVICE_STATUS = "DEVICE_STATUS"
    const val HELP = "HELP"
    const val ADVICE = "ADVICE"

    private val allowed = setOf(
        PROFILE_BALANCED,
        PROFILE_INTERPOLATION,
        PROFILE_X4,
        OPEN_INSTALLED_GAME,
        DEVICE_STATUS,
        HELP,
        ADVICE
    )

    fun validate(candidate: LocalAiActionCandidate): Boolean {
        if (candidate.action !in allowed) return false

        val argument = candidate.argument?.trim()
        return when (candidate.action) {
            PROFILE_BALANCED,
            PROFILE_INTERPOLATION,
            PROFILE_X4,
            DEVICE_STATUS,
            HELP,
            ADVICE -> argument.isNullOrEmpty()

            OPEN_INSTALLED_GAME ->
                !argument.isNullOrEmpty() &&
                    argument.length <= 200 &&
                    argument.all { it.isLetterOrDigit() || it.isWhitespace() || it in "._:-" }

            else -> false
        }
    }
}
