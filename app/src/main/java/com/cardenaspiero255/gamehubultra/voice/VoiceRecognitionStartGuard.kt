package com.cardenaspiero255.gamehubultra.voice

import android.speech.SpeechRecognizer

/**
 * Keeps recognizer startup failures inside Ultra's normal error channel.
 * Cleanup is supplied by the Android adapter so the policy remains unit-testable.
 */
internal object VoiceRecognitionStartGuard {
    fun run(
        onListeningChanged: (Boolean) -> Unit,
        onError: (Int) -> Unit,
        cleanup: () -> Unit,
        action: () -> Unit
    ): Boolean =
        try {
            action()
            true
        } catch (_: RuntimeException) {
            runCatching(cleanup)
            onListeningChanged(false)
            onError(SpeechRecognizer.ERROR_CLIENT)
            false
        }
}
