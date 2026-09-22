package com.cardenaspiero255.gamehubultra.voice

import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile

sealed interface VoiceCommand {
    data class OpenGame(
        val query: String,
        val requestedProfile: PerformanceProfile? = null
    ) : VoiceCommand

    data class SelectProfile(val profile: PerformanceProfile) : VoiceCommand
    data object DeviceStatus : VoiceCommand
    data object Help : VoiceCommand
    data class Unknown(val transcript: String) : VoiceCommand
}

interface NaturalLanguageIntentResolver {
    fun resolve(transcript: String): VoiceCommand?
}
