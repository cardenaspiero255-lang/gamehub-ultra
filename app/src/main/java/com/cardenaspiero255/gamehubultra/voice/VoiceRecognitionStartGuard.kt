package com.cardenaspiero255.gamehubultra.voice

import android.speech.SpeechRecognizer

/**
 * Converts vendor/framework recognizer startup failures into the normal voice
 * error channel so a broken speech service cannot terminate the app.
 */
internal object VoiceRecognitionStartGuard {
    fun run(
        onError: (Int) -> Unit,
        action: () -> Unit
    ): Boolean =
        try {
            action()
            true
        } catch (_: RuntimeException) {
            onError(SpeechRecognizer.ERROR_CLIENT)
            false
        }
}
