package com.cardenaspiero255.gamehubultra.voice

/** Android READY is not proof of ASR recovery; require a nonblank transcript. */
internal object UltraWakeRecognitionHealth {
    fun shouldRebuildRecognizer(error: Int): Boolean =
        error == android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
            error == android.speech.SpeechRecognizer.ERROR_CLIENT

    fun hasTranscript(transcripts: List<String>?): Boolean =
        transcripts?.any { it.isNotBlank() } == true
}
