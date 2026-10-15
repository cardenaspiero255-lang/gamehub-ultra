package com.cardenaspiero255.gamehubultra.voice

/** Android READY is not proof of ASR recovery; require a nonblank transcript. */
internal object UltraWakeRecognitionHealth {
    fun hasTranscript(transcripts: List<String>?): Boolean =
        transcripts?.any { it.isNotBlank() } == true
}
