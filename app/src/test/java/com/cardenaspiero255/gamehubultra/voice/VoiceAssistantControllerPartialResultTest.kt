package com.cardenaspiero255.gamehubultra.voice

import kotlin.test.assertTrue
import org.junit.Test

class VoiceAssistantControllerPartialResultTest {
    @Test
    fun voiceControllerExposesPartialTranscriptContract() {
        val hasPartialTranscriptCallback = VoiceAssistantController::class.java
            .declaredConstructors
            .flatMap { constructor -> constructor.parameters.asList() }
            .any { parameter -> parameter.name == "onPartialTranscript" }

        assertTrue(
            hasPartialTranscriptCallback,
            "VoiceAssistantController debe exponer un callback onPartialTranscript antes de poder entregar resultados parciales."
        )
    }
}
