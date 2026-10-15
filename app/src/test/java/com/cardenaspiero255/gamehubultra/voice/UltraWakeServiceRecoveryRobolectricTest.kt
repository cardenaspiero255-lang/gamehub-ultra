package com.cardenaspiero255.gamehubultra.voice

import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class UltraWakeServiceRecoveryRobolectricTest {
    private fun listener(service: UltraWakeService): RecognitionListener =
        UltraWakeService::class.java.getDeclaredField("listener")
            .apply { isAccessible = true }
            .get(service) as RecognitionListener

    private fun recovery(service: UltraWakeService): UltraWakeRecoveryPolicy =
        UltraWakeService::class.java.getDeclaredField("recoveryPolicy")
            .apply { isAccessible = true }
            .get(service) as UltraWakeRecoveryPolicy

    @Test
    fun busyRecognizerFailureUsesBackoffAndHealthyCallbackResetsIt() {
        val service = UltraWakeService()
        val listener = listener(service)
        val recovery = recovery(service)

        listener.onError(SpeechRecognizer.ERROR_RECOGNIZER_BUSY)
        listener.onError(SpeechRecognizer.ERROR_RECOGNIZER_BUSY)
        assertEquals(2_000L, recovery.nextDelayMillis())

        listener.onReadyForSpeech(null)
        assertEquals(500L, recovery.nextDelayMillis())
    }

    @Test
    fun destroyedServiceIgnoresStaleRecognizerErrorCallbacks() {
        val service = UltraWakeService()
        val stopped = UltraWakeService::class.java.getDeclaredField("stopped")
            .apply { isAccessible = true }
        stopped.setBoolean(service, true)

        listener(service).onError(SpeechRecognizer.ERROR_CLIENT)
        assertEquals(500L, recovery(service).nextDelayMillis())
    }

    @Test
    fun shutdownBeforeStartupDoesNotInitializeUnusedAiAdvisor() {
        val service = UltraWakeService()
        val advisor = UltraWakeService::class.java.getDeclaredField("aiAdvisorLazy")
            .apply { isAccessible = true }
            .get(service) as Lazy<*>
        kotlin.test.assertFalse(advisor.isInitialized())
        service.onDestroy()
        kotlin.test.assertFalse(advisor.isInitialized())
    }

    @Test
    fun teardownContinuesWhenAlreadyBrokenRecognizerThrowsOnCancel() {
        val service = UltraWakeService()
        val recognizer = org.mockito.Mockito.mock(SpeechRecognizer::class.java)
        org.mockito.Mockito.doThrow(IllegalStateException("Recognizer already closed"))
            .`when`(recognizer).cancel()
        UltraWakeService::class.java.getDeclaredField("recognizer")
            .apply { isAccessible = true }
            .set(service, recognizer)

        service.onDestroy()

        org.mockito.Mockito.verify(recognizer).destroy()
    }

    @Test
    fun nonBusySpeechErrorsAlsoUseBoundedRetryInsteadOfImmediateSpin() {
        val service = UltraWakeService()
        listener(service).onError(SpeechRecognizer.ERROR_NETWORK)
        assertEquals(1_000L, recovery(service).nextDelayMillis())
    }
}
