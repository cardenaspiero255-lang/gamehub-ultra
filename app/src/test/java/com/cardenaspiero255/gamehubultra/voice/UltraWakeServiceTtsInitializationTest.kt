package com.cardenaspiero255.gamehubultra.voice

import android.os.Bundle
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class UltraWakeServiceTtsInitializationTest {
    private fun call(service: UltraWakeService, name: String, vararg args: Any) {
        val method = UltraWakeService::class.java.declaredMethods
            .first { it.name == name && it.parameterCount == args.size }
        method.isAccessible = true
        method.invoke(service, *args)
    }

    private fun speechController(service: UltraWakeService): UltraWakeSpeechPlaybackController {
        val getter = UltraWakeService::class.java.declaredMethods
            .first { it.name == "getSpeechPlayback" }
        getter.isAccessible = true
        return getter.invoke(service) as UltraWakeSpeechPlaybackController
    }

    @Test
    fun firstGamingAnswerIsQueuedUntilBackgroundTtsIsReady() {
        val service = UltraWakeService()
        val tts = Mockito.mock(TextToSpeech::class.java)
        val playback = speechController(service)
        playback.attach(tts)

        call(service, "speakAndResume", "Comparación completa de dos móviles.")
        assertTrue(Mockito.mockingDetails(tts).invocations.none { it.method.name == "speak" })

        call(service, "handleTtsInitialization", TextToSpeech.SUCCESS)
        assertEquals(1, Mockito.mockingDetails(tts).invocations.count {
            it.method.name == "speak"
        })
        call(service, "handleTtsInitialization", TextToSpeech.SUCCESS)
        assertEquals(1, Mockito.mockingDetails(tts).invocations.count {
            it.method.name == "speak"
        })
        playback.shutdown()
    }

    @Test
    fun stopBeforeTtsIsReadyNeverReplaysCancelledResponse() {
        val service = UltraWakeService()
        val tts = Mockito.mock(TextToSpeech::class.java)
        val playback = speechController(service)
        playback.attach(tts)
        val coordinator = UltraWakeService::class.java.getDeclaredField("commandCoordinator")
            .apply { isAccessible = true }
            .get(service) as UltraWakeCommandCoordinator
        assertTrue(coordinator.tryBeginCommand(keepRecognitionActive = true))
        call(service, "speakAndResume", "Esta respuesta debe cancelarse.")
        val heard = Bundle().apply {
            putStringArrayList(
                SpeechRecognizer.RESULTS_RECOGNITION,
                arrayListOf("Ultra, para")
            )
        }
        call(service, "handleRecognitionBundle", heard, true)
        kotlin.test.assertFalse(coordinator.isCommandRunning())
        call(service, "handleTtsInitialization", TextToSpeech.SUCCESS)
        assertEquals(0, Mockito.mockingDetails(tts).invocations.count {
            it.method.name == "speak"
        })
        call(service, "speakAndResume", "Respuesta nueva después de cancelar.")
        assertEquals(1, Mockito.mockingDetails(tts).invocations.count {
            it.method.name == "speak"
        })
        playback.shutdown()
    }

    @Test
    fun failedBackgroundTtsInitializationReleasesPendingPlayback() {
        val service = UltraWakeService()
        val playback = speechController(service)
        call(service, "speakAndResume", "No puede quedar atascada.")
        val generation = UltraWakeService::class.java.getDeclaredField("speechGeneration")
            .apply { isAccessible = true }
            .get(service) as UltraWakeSpeechGeneration
        call(service, "handleTtsInitialization", TextToSpeech.ERROR)
        assertTrue(!generation.isActive())
        playback.shutdown()
    }
}
