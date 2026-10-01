package com.cardenaspiero255.gamehubultra.voice

import android.content.Intent
import android.os.Build
import android.speech.RecognizerIntent

internal object UltraWakeRecognitionIntentFactory {
    fun create(
        languageTag: String,
        mode: UltraWakeRecognitionMode,
        persistentSource: UltraPersistentSpeechSource?
    ): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)

            if (mode == UltraWakeRecognitionMode.LEGACY_RESTARTING) {
                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                    1200L
                )
                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                    850L
                )
            }

            if (
                mode == UltraWakeRecognitionMode.PERSISTENT_SEGMENTED &&
                persistentSource != null &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            ) {
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, persistentSource.readDescriptor)
                putExtra(
                    RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT,
                    UltraPersistentSpeechSource.CHANNEL_COUNT
                )
                putExtra(
                    RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING,
                    UltraPersistentSpeechSource.ENCODING
                )
                putExtra(
                    RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE,
                    UltraPersistentSpeechSource.SAMPLE_RATE_HZ
                )
                putExtra(
                    RecognizerIntent.EXTRA_SEGMENTED_SESSION,
                    RecognizerIntent.EXTRA_AUDIO_SOURCE
                )
                putStringArrayListExtra(
                    RecognizerIntent.EXTRA_BIASING_STRINGS,
                    arrayListOf("Ultra", "ultra")
                )
            }
        }
}
