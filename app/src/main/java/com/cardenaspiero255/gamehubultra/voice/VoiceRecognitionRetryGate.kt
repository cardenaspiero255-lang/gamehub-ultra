package com.cardenaspiero255.gamehubultra.voice

/** Prevents synchronous SpeechRecognizer error callbacks from recursively retrying. */
internal class VoiceRecognitionRetryGate {
    private var pending = false

    @Synchronized
    fun trySchedule(): Boolean {
        if (pending) return false
        pending = true
        return true
    }

    @Synchronized
    fun onRetryDispatched() {
        pending = false
    }

    @Synchronized
    fun reset() {
        pending = false
    }
}
