package com.cardenaspiero255.gamehubultra.ui.voice

import kotlin.test.Test
import kotlin.test.assertEquals

class VoiceAssistantPresentationTest {

    @Test
    fun knownBrandNamesAreNormalizedForPresentationOnly() {
        assertEquals(
            "Qué es TikTok",
            presentVoiceTranscript("Qué es tik Tok")
        )
        assertEquals(
            "abre YouTube",
            presentVoiceTranscript("abre you tube")
        )
        assertEquals(
            "abre WhatsApp",
            presentVoiceTranscript("abre whats app")
        )
    }
}
