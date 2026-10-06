package com.cardenaspiero255.gamehubultra.voice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UltraWakeStopSpeakingCar44Test {
    @Test
    fun `short Spanish stop phrases are recognized without requiring wake word`() {
        listOf(
            "detente",
            "para",
            "cállate",
            "silencio",
            "Ultra detente",
            "Ultra, para"
        ).forEach { phrase ->
            assertTrue(UltraWakeStopSpeakingIntent.matches(phrase), phrase)
        }
    }

    @Test
    fun `ordinary sentences containing para do not stop speech`() {
        listOf(
            "para qué sirve Vulkan",
            "qué perfil recomiendas para jugar",
            "Ultra abre el juego para probarlo"
        ).forEach { phrase ->
            assertFalse(UltraWakeStopSpeakingIntent.matches(phrase), phrase)
        }
    }

    @Test
    fun `stop phrase interrupts playback without becoming another command`() {
        assertEquals(
            UltraWakeRecognitionDisposition.STOP_TTS,
            UltraWakeBargeInPolicy.decide(
                playbackActive = true,
                transcript = "detente",
                playbackEcho = false
            )
        )
        assertEquals(
            UltraWakeRecognitionDisposition.STOP_TTS,
            UltraWakeBargeInPolicy.decide(
                playbackActive = true,
                transcript = "Ultra para",
                playbackEcho = false
            )
        )
    }
}
