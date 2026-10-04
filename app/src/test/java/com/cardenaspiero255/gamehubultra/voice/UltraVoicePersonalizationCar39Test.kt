package com.cardenaspiero255.gamehubultra.voice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UltraVoicePersonalizationCar39Test {
    @Test
    fun ambiguousGameAliasIsRejectedInsteadOfGuessing() {
        val index = UltraVoiceAliasIndex(
            gameAliases = listOf(
                UltraGameVoiceAlias("resi", "com.example.re4"),
                UltraGameVoiceAlias("resi", "com.example.re2")
            )
        )
        assertNull(index.resolveGame("resi"))
    }

    @Test
    fun customPhraseResolvesOnlyAllowListedAction() {
        val preferences = UltraVoicePhrasePreferences(
            phrases = mapOf("modo tranquilo" to UltraSafeVoiceAction.SelectBalancedProfile)
        )
        assertEquals(UltraSafeVoiceAction.SelectBalancedProfile, preferences.resolve("modo tranquilo"))
        assertNull(preferences.resolve("accion no registrada"))
    }
}
