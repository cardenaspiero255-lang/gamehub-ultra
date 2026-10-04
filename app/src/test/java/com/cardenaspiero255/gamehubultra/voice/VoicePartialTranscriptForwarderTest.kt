package com.cardenaspiero255.gamehubultra.voice

import kotlin.test.assertEquals
import org.junit.Test

class VoicePartialTranscriptForwarderTest {
    @Test
    fun forwardsTrimmedFirstNonBlankCandidate() {
        val partials = mutableListOf<String>()

        VoicePartialTranscriptForwarder.forward(
            alternatives = listOf("  Ultra abre  ", "Ultra"),
            onPartialTranscript = partials::add
        )

        assertEquals(listOf("Ultra abre"), partials)
    }

    @Test
    fun ignoresMissingEmptyAndBlankFirstCandidate() {
        val partials = mutableListOf<String>()

        VoicePartialTranscriptForwarder.forward(null, partials::add)
        VoicePartialTranscriptForwarder.forward(emptyList(), partials::add)
        VoicePartialTranscriptForwarder.forward(listOf("   ", "Ultra"), partials::add)

        assertEquals(emptyList(), partials)
    }
}
