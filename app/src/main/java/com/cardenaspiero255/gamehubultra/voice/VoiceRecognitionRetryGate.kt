package com.cardenaspiero255.gamehubultra.voice

/** Prevents synchronous SpeechRecognizer error callbacks from recursively retrying. */
internal class VoiceRecognitionRetryGate {
    private var pending = false
    private var closed = false

    @Synchronized
    fun trySchedule(): Boolean {
        if (closed || pending) return false
        pending = true
        return true
    }

    @Synchronized
    fun onRetryDispatched() {
        if (!closed) pending = false
    }

    @Synchronized
    fun reset() {
        if (!closed) pending = false
    }

    /** Terminal state: callbacks after controller.release() cannot schedule retries. */
    @Synchronized
    fun close() {
        closed = true
        pending = false
    }
}
