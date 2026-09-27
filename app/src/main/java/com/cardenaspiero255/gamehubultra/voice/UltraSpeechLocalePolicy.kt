package com.cardenaspiero255.gamehubultra.voice

import android.speech.tts.TextToSpeech
import java.util.Locale

object UltraSpeechLocalePolicy {
    const val PREFERRED_TAG = "es-CL"
    const val FALLBACK_TAG = "es"

    fun preferredLocale(): Locale = Locale.forLanguageTag(PREFERRED_TAG)

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
