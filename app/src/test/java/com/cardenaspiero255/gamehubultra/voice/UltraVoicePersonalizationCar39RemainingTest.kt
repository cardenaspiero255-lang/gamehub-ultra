package com.cardenaspiero255.gamehubultra.voice

import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UltraVoicePersonalizationCar39RemainingTest {
    @Test
    fun profileAliasConflictIsRejectedInsteadOfGuessing() {
        val index = UltraVoiceProfileAliasIndex(
            listOf(
                UltraProfileVoiceAlias("turbo", PerformanceProfile.X4),
                UltraProfileVoiceAlias("turbo", PerformanceProfile.BALANCED)
            )
        )
        assertNull(index.resolveProfile("turbo"))
    }

    @Test
    fun preferredCommandIsScopedToItsGame() {
        val preferences = UltraPerGameVoicePreferences(
            mapOf("com.example.re4" to UltraSafeVoiceAction.SelectBalancedProfile)
        )
        assertEquals(
            UltraSafeVoiceAction.SelectBalancedProfile,
            preferences.preferredAction("com.example.re4")
        )
        assertNull(preferences.preferredAction("com.example.re2"))
    }

    @Test
    fun exportImportRoundTripContainsOnlyVoicePreferences() {
        val bundle = UltraVoicePreferenceBundle(
            profileAliases = listOf(UltraProfileVoiceAlias("tranquilo", PerformanceProfile.BALANCED)),
            phrases = mapOf("modo tranquilo" to UltraSafeVoiceAction.SelectBalancedProfile),
            perGamePreferredActions = mapOf("com.example.re4" to UltraSafeVoiceAction.SelectBalancedProfile)
        )
        val exported = UltraVoicePreferenceCodec.export(bundle)
        assertTrue("token" !in exported.lowercase())
        assertTrue("secret" !in exported.lowercase())
        assertEquals(bundle, UltraVoicePreferenceCodec.import(exported))
    }

    @Test
    fun malformedOrUnknownImportedActionIsRejected() {
        assertNull(UltraVoicePreferenceCodec.import("v1|phrase|hola|DELETE_DATA"))
        assertNull(UltraVoicePreferenceCodec.import("not-a-supported-format"))
    }
}
