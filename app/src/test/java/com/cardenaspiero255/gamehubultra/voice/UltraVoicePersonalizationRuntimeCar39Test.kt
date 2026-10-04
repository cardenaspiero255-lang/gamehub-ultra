package com.cardenaspiero255.gamehubultra.voice

import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class UltraVoicePersonalizationRuntimeCar39Test {
    @Test
    fun personalizedPhraseBecomesSafeRuntimeCommand() {
        val preferences = UltraVoicePreferenceBundle(
            phrases = mapOf("modo tranquilo" to UltraSafeVoiceAction.SelectBalancedProfile)
        )

        val command = UltraVoicePersonalizationResolver.resolve(
            transcript = "Ultra, modo tranquilo",
            selectedGamePackage = null,
            preferences = preferences
        )

        assertEquals(
            PerformanceProfile.BALANCED,
            assertIs<VoiceCommand.SelectProfile>(command).profile
        )
    }

    @Test
    fun perGamePreferenceAppliesOnlyInsideMatchingGame() {
        val preferences = UltraVoicePreferenceBundle(
            perGamePreferredActions = mapOf(
                "com.example.re4" to UltraSafeVoiceAction.SelectBalancedProfile
            )
        )

        val matching = UltraVoicePersonalizationResolver.resolvePreferredForGame(
            selectedGamePackage = "com.example.re4",
            preferences = preferences
        )
        val other = UltraVoicePersonalizationResolver.resolvePreferredForGame(
            selectedGamePackage = "com.example.re2",
            preferences = preferences
        )

        assertIs<VoiceCommand.SelectProfile>(matching)
        assertEquals(null, other)
    }
}
