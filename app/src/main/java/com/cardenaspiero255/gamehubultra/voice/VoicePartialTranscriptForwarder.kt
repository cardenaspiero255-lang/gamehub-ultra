package com.cardenaspiero255.gamehubultra.voice

internal object VoicePartialTranscriptForwarder {
    fun forward(
        alternatives: List<String>?,
        onPartialTranscript: (String) -> Unit
    ) {
        alternatives
            ?.firstOrNull()
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let(onPartialTranscript)
    }
}
