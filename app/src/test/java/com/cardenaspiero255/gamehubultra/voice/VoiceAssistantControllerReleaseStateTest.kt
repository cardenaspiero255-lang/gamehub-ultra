package com.cardenaspiero255.gamehubultra.voice

import android.speech.RecognitionListener
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class VoiceAssistantControllerReleaseStateTest {
    @Test
    fun releaseEndsListeningState() {
        val listeningStates = mutableListOf<Boolean>()
        val controller = VoiceAssistantController(
            context = RuntimeEnvironment.getApplication(),
            onListeningChanged = { listeningStates += it },
            onTranscript = {},
            onError = {}
        )

        val listenerField = VoiceAssistantController::class.java
            .getDeclaredField("listener")
            .apply { isAccessible = true }
        val listener = listenerField.get(controller) as RecognitionListener
        listener.onReadyForSpeech(null)

        controller.release()

        assertEquals(listOf(false), listeningStates)
    }
}
