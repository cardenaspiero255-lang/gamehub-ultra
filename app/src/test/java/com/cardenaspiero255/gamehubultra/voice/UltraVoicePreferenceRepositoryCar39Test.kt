package com.cardenaspiero255.gamehubultra.voice

import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import kotlin.test.Test
import kotlin.test.assertEquals

class UltraVoicePreferenceRepositoryCar39Test {
    @Test
    fun repositoryRoundTripRestoresVoicePreferences() {
        var stored: String? = null
        val repository = CodecBackedUltraVoicePreferenceRepository(
            read = { stored },
            write = { stored = it }
        )
        val expected = UltraVoicePreferenceBundle(
            profileAliases = listOf(
                UltraProfileVoiceAlias("tranquilo", PerformanceProfile.BALANCED)
            ),
            phrases = mapOf("modo tranquilo" to UltraSafeVoiceAction.SelectBalancedProfile),
            perGamePreferredActions = mapOf(
                "com.example.re4" to UltraSafeVoiceAction.SelectBalancedProfile
            )
        )

        repository.save(expected)

        assertEquals(expected, repository.load())
    }

    @Test
    fun corruptedStoredPreferencesFailClosedToEmpty() {
        val repository = CodecBackedUltraVoicePreferenceRepository(
            read = { "v1|phrase|hola|DELETE_DATA" },
            write = {}
        )

        assertEquals(UltraVoicePreferenceBundle(), repository.load())
    }
}
