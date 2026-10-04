package com.cardenaspiero255.gamehubultra.voice

import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class UltraVoicePersonalizationCar39CompletionTest {
    @Test
    fun profileAliasResolvesToRuntimeProfileCommand() {
        val preferences = UltraVoicePreferenceBundle(
            profileAliases = listOf(
                UltraProfileVoiceAlias("tranquilo", PerformanceProfile.BALANCED)
            )
        )

        assertEquals(
            VoiceCommand.SelectProfile(PerformanceProfile.BALANCED),
            UltraVoicePersonalizationResolver.resolve(
                transcript = "Ultra pon tranquilo",
                selectedGamePackage = null,
                preferences = preferences
            )
        )
    }

    @Test
    fun perGamePreferredPhraseOnlyResolvesInsideMatchingGame() {
        val preferences = UltraVoicePreferenceBundle(
            perGamePreferredActions = mapOf(
                "com.supercell.brawlstars" to UltraSafeVoiceAction.SelectBalancedProfile
            )
        )

        assertEquals(
            VoiceCommand.SelectProfile(PerformanceProfile.BALANCED),
            UltraVoicePersonalizationResolver.resolvePreferredForGame(
                selectedGamePackage = "com.supercell.brawlstars",
                preferences = preferences
            )
        )
        assertEquals(
            null,
            UltraVoicePersonalizationResolver.resolvePreferredForGame(
                selectedGamePackage = "com.other.game",
                preferences = preferences
            )
        )
    }
}
