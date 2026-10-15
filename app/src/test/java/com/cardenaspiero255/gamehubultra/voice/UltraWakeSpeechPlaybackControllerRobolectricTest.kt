package com.cardenaspiero255.gamehubultra.voice

import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class UltraWakeSpeechPlaybackControllerRobolectricTest {
    private fun spoken(tts: TextToSpeech) =
        Mockito.mockingDetails(tts).invocations
            .filter { it.method.name == "speak" }

    private fun listener(tts: TextToSpeech): UtteranceProgressListener =
        Mockito.mockingDetails(tts).invocations
            .first { it.method.name == "setOnUtteranceProgressListener" }
            .arguments[0] as UtteranceProgressListener

    @Test
    fun longGamingComparisonQueuesAllSegmentsAndFinishesOnlyAfterLastOne() {
        val generation = UltraWakeSpeechGeneration()
        val guard = UltraWakePlaybackGuard()
        val completions = mutableListOf<Long>()
        val tts = Mockito.mock(TextToSpeech::class.java)
        val controller = UltraWakeSpeechPlaybackController(
            Handler(Looper.getMainLooper()), generation, guard
        ) { token ->
            if (generation.complete(token)) {
                completions += token
                guard.onPlaybackFinished(System.currentTimeMillis())
            }
        }
        controller.attach(tts)
        val comparison = "El modelo usa fuentes independientes. ".repeat(125)
        controller.speak(comparison)

        val calls = spoken(tts)
        assertTrue(calls.size >= 3)
        assertEquals(TextToSpeech.QUEUE_FLUSH, calls.first().arguments[1])
        assertTrue(calls.drop(1).all { it.arguments[1] == TextToSpeech.QUEUE_ADD })
        assertEquals(
            comparison.trim(),
            calls.joinToString(" ") { it.arguments[0] as String }
        )
        assertTrue(calls.all { (it.arguments[0] as String).length <= 800 })
        val callback = listener(tts)
        val intermediateId = calls.first().arguments[3] as String
        val finalId = calls.last().arguments[3] as String

        callback.onDone(intermediateId)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue(completions.isEmpty())
        assertTrue(generation.isActive())
        assertTrue(guard.isPlaybackActive())

        callback.onDone(finalId)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, completions.size)
        assertFalse(generation.isActive())
        assertFalse(guard.isPlaybackActive())
        controller.shutdown()
    }

    @Test
    fun emptyAndUnavailableTtsCompleteImmediatelyRatherThanLeavingWakeBlocked() {
        val generation = UltraWakeSpeechGeneration()
        val done = mutableListOf<Long>()
        val controller = UltraWakeSpeechPlaybackController(
            Handler(Looper.getMainLooper()), generation,
            UltraWakePlaybackGuard()
        ) { token ->
            done += token
            generation.complete(token)
        }
        controller.speak("The engine is not attached")
        assertEquals(1, done.size)
        val tts = Mockito.mock(TextToSpeech::class.java)
        controller.attach(tts)
        controller.speak(" ")
        assertEquals(2, done.size)
        assertTrue(spoken(tts).isEmpty())
        controller.stop()
        controller.shutdown()
        Mockito.verify(tts, Mockito.atLeastOnce()).stop()
        Mockito.verify(tts).shutdown()
    }

    @Test
    fun delayedCompletionFromOldAnswerCannotCompleteNewAnswer() {
        val generation = UltraWakeSpeechGeneration()
        val completed = mutableListOf<Long>()
        val controller = UltraWakeSpeechPlaybackController(
            Handler(Looper.getMainLooper()), generation,
            UltraWakePlaybackGuard()
        ) { token ->
            if (generation.complete(token)) completed += token
        }
        val tts = Mockito.mock(TextToSpeech::class.java)
        controller.attach(tts)
        controller.speak("Antigua.")
        val oldId = spoken(tts).last().arguments[3] as String
        controller.speak("Actual.")
        val newId = spoken(tts).last().arguments[3] as String

        listener(tts).onDone(oldId)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue(completed.isEmpty())
        assertTrue(generation.isActive())
        listener(tts).onDone(newId)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, completed.size)
    }
}
