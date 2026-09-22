package com.cardenaspiero255.gamehubultra.voice

import kotlin.test.Test
import kotlin.test.assertEquals

class VoiceInteractionProtocolTest {
    @Test
    fun commandIdentifierIsStable() {
        assertEquals(
            "com.cardenaspiero255.gamehubultra.EXECUTE_VOICE_COMMAND",
            GameHubVoiceInteractionSessionService.COMMAND_EXECUTE_TEXT
        )
    }

    @Test
    fun transcriptExtraIdentifierIsStable() {
        assertEquals(
            "transcript",
            GameHubVoiceInteractionSessionService.EXTRA_TRANSCRIPT
        )
    }
}
