package com.cardenaspiero255.gamehubultra.voice

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UltraWakeRecognitionHealthTest {
    @Test
    fun nullOrEmptyResultsCannotResetBackoff() {
        assertFalse(UltraWakeRecognitionHealth.hasTranscript(null))
        assertFalse(UltraWakeRecognitionHealth.hasTranscript(emptyList()))
    }

    @Test
    fun whitespaceOnlyResultsCannotResetBackoff() {
        assertFalse(UltraWakeRecognitionHealth.hasTranscript(listOf("", " ", "\t")))
    }

    @Test
    fun anyActualTranscriptionCountsAsHealthy() {
        assertTrue(UltraWakeRecognitionHealth.hasTranscript(listOf(" ", "hola Ultra")))
    }
}
