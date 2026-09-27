package com.cardenaspiero255.gamehubultra.voice

import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

object UltraSpeechLocalePolicy {
    const val PREFERRED_TAG = "es-CL"
    const val FALLBACK_TAG = "es"

    fun preferredLocale(): Locale = Locale.forLanguageTag(PREFERRED_TAG)

    fun fallbackRecognitionTag(
        error: Int,
        currentTag: String
    ): String? {
        if (currentTag != PREFERRED_TAG) return null
        return when (error) {
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> FALLBACK_TAG
            else -> null
        }
    }

    fun applyTo(tts: TextToSpeech) {
        val preferredResult = tts.setLanguage(preferredLocale())
        if (
            preferredResult == TextToSpeech.LANG_MISSING_DATA ||
            preferredResult == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            tts.setLanguage(Locale.forLanguageTag(FALLBACK_TAG))
        }
    }
}
