package com.cardenaspiero255.gamehubultra.voice

import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class UltraContextualVoiceCar37Test {
    @Test
    fun openThisGameUsesSelectedGameContext() {
        val command = UltraContextualVoiceResolver.resolve(
            transcript = "Ultra, abre este juego",
            context = UltraVoiceContext(selectedGamePackage = "com.example.re4")
        )

        val open = assertIs<VoiceCommand.OpenGame>(command)
        assertEquals("com.example.re4", open.query)
    }

    @Test
    fun shortBalancedCommandTargetsCurrentSelection() {
        val command = UltraContextualVoiceResolver.resolve(
            transcript = "Ultra, modo equilibrado",
            context = UltraVoiceContext(selectedGamePackage = "com.example.re4")
        )

        val profile = assertIs<VoiceCommand.SelectProfile>(command)
        assertEquals(PerformanceProfile.BALANCED, profile.profile)
    }

    @Test
    fun thermalStatusUsesCurrentDiagnosticsWithoutInventingGameContext() {
        assertEquals(
            VoiceCommand.DeviceStatus,
            UltraContextualVoiceResolver.resolve(
                transcript = "Ultra, estado térmico",
                context = UltraVoiceContext(
                    selectedGamePackage = "com.example.re4",
                    thermalLabel = "moderada"
                )
            )
        )
    }

    @Test
    fun thisGameWithoutSelectionStaysUnknown() {
        assertIs<VoiceCommand.Unknown>(
            UltraContextualVoiceResolver.resolve(
                transcript = "Ultra, abre este juego",
                context = UltraVoiceContext()
            )
        )
    }
}
