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
    fun readyThenErrorRepeatedlyDoesNotResetBackoff() {
        val service = UltraWakeService()
        val listener = listener(service)
        val recovery = recovery(service)

        listener.onReadyForSpeech(null)
        listener.onError(SpeechRecognizer.ERROR_RECOGNIZER_BUSY)
        listener.onReadyForSpeech(null)
        listener.onError(SpeechRecognizer.ERROR_RECOGNIZER_BUSY)
        assertEquals(2_000L, recovery.nextDelayMillis())
    }

    @Test
    fun emptyRecognitionIsNotProofOfHealthyMicAndDoesNotResetBackoff() {
        val service = UltraWakeService()
        val listener = listener(service)
        val recovery = recovery(service)
        listener.onError(SpeechRecognizer.ERROR_NETWORK)
        listener.onResults(android.os.Bundle())
        assertEquals(1_000L, recovery.nextDelayMillis())
    }

    @Test
    fun meaningfulRecognitionResultResetsAfterFailures() {
        val service = UltraWakeService()
        val listener = listener(service)
        val recovery = recovery(service)
        listener.onError(SpeechRecognizer.ERROR_RECOGNIZER_BUSY)
        listener.onError(SpeechRecognizer.ERROR_RECOGNIZER_BUSY)
        listener.onReadyForSpeech(null)
        val results = android.os.Bundle().apply {
            putStringArrayList(
                SpeechRecognizer.RESULTS_RECOGNITION,
                arrayListOf("hola, estoy aquí")
            )
        }
        listener.onResults(results)
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
    fun validSegmentedTranscriptionResetsBackoffAfterNetworkFailure() {
        val service = UltraWakeService()
        val recovery = recovery(service)
        listener(service).onError(SpeechRecognizer.ERROR_NETWORK)
        UltraWakeService::class.java.getDeclaredField("persistentSessionActive")
            .apply { isAccessible = true }
            .setBoolean(service, true)
        val segment = android.os.Bundle().apply {
            putStringArrayList(
                SpeechRecognizer.RESULTS_RECOGNITION,
                arrayListOf("sin comando de activación")
            )
        }
        listener(service).onSegmentResults(segment)
        assertEquals(500L, recovery.nextDelayMillis())
    }

    @Test
    fun busyRecognizerIsDestroyedBeforeNextRecoveryAttempt() {
        val service = UltraWakeService()
        val recognizer = org.mockito.Mockito.mock(SpeechRecognizer::class.java)
        val field = UltraWakeService::class.java.getDeclaredField("recognizer")
            .apply { isAccessible = true }
        field.set(service, recognizer)
        listener(service).onError(SpeechRecognizer.ERROR_RECOGNIZER_BUSY)
        org.mockito.Mockito.verify(recognizer).destroy()
        kotlin.test.assertNull(field.get(service))
    }

    @Test
    fun blankSegmentedTranscriptionNeverResetsFailureBackoff() {
        val service = UltraWakeService()
        val recovery = recovery(service)
        listener(service).onError(SpeechRecognizer.ERROR_NETWORK)
        UltraWakeService::class.java.getDeclaredField("persistentSessionActive")
            .apply { isAccessible = true }
            .setBoolean(service, true)
        val segment = android.os.Bundle().apply {
            putStringArrayList(
                SpeechRecognizer.RESULTS_RECOGNITION,
                arrayListOf(" ", "")
            )
        }
        listener(service).onSegmentResults(segment)
        assertEquals(1_000L, recovery.nextDelayMillis())
    }

    @Test
    fun clientSpeechRecognizerFailureDestroysPoisonedInstance() {
        val service = UltraWakeService()
        val recognizer = org.mockito.Mockito.mock(SpeechRecognizer::class.java)
        val field = UltraWakeService::class.java.getDeclaredField("recognizer")
            .apply { isAccessible = true }
        field.set(service, recognizer)
        listener(service).onError(SpeechRecognizer.ERROR_CLIENT)
        org.mockito.Mockito.verify(recognizer).destroy()
        kotlin.test.assertNull(field.get(service))
    }

    @Test
    fun actualForegroundLifecycleCanStartAndShutDownWithoutCrashing() {
        val controller = org.robolectric.Robolectric.buildService(UltraWakeService::class.java)
        try {
            val service = controller.create().get()
            val startIntent = android.content.Intent(service, UltraWakeService::class.java)
                .setAction(UltraWakeService.ACTION_START)
            assertEquals(android.app.Service.START_NOT_STICKY,
                service.onStartCommand(startIntent, 0, 1))
        } finally {
            controller.destroy()
        }
    }

    private fun rejectForegroundPromotion(
        service: UltraWakeService,
        failure: RuntimeException
    ): UltraWakeForegroundController {
        val foreground = org.mockito.Mockito.mock(UltraWakeForegroundController::class.java)
        org.mockito.Mockito.doThrow(failure).`when`(foreground).ensureForeground()
        UltraWakeService::class.java.getDeclaredField("foregroundController" + ')
            .apply { isAccessible = true }
            .set(service, lazyOf(foreground))
        return foreground
    }

    @Test
    fun rejectedForegroundMicDuringOnCreateDisablesStoredListeningWithoutInitializingTts() {
        val controller = org.robolectric.Robolectric.buildService(UltraWakeService::class.java)
        val service = controller.get()
        val gateway = AndroidContinuousVoiceGateway(service)
        gateway.setEnabled(true)
        val foreground = rejectForegroundPromotion(
            service, SecurityException("RECORD_AUDIO revoked during foreground promotion")
        )

        try {
            controller.create()

            org.mockito.Mockito.verify(foreground).ensureForeground()
            kotlin.test.assertFalse(gateway.isEnabled())
            val advisor = UltraWakeService::class.java.getDeclaredField("aiAdvisorLazy")
                .apply { isAccessible = true }
                .get(service) as Lazy<*>
            kotlin.test.assertFalse(advisor.isInitialized())
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun rejectedForegroundMicOnStartCommandDisablesStoredListeningAndReturnsNotSticky() {
        val controller = org.robolectric.Robolectric.buildService(UltraWakeService::class.java)
        val service = controller.get()
        val gateway = AndroidContinuousVoiceGateway(service)
        gateway.setEnabled(true)
        val foreground = rejectForegroundPromotion(
            service, IllegalStateException("Mic foreground service prohibited in background")
        )
        val start = android.content.Intent(service, UltraWakeService::class.java)
            .setAction(UltraWakeService.ACTION_START)

        try {
            assertEquals(android.app.Service.START_NOT_STICKY,
                service.onStartCommand(start, 0, 1))
            org.mockito.Mockito.verify(foreground).ensureForeground()
            kotlin.test.assertFalse(gateway.isEnabled())
        } finally {
            controller.destroy()
        }
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
 + "delegate")
            .apply { isAccessible = true }
            .set(service, lazyOf(foreground))
        return foreground
    }

    @Test
    fun rejectedForegroundMicDuringOnCreateDisablesStoredListeningWithoutInitializingTts() {
        val controller = org.robolectric.Robolectric.buildService(UltraWakeService::class.java)
        val service = controller.get()
        val gateway = AndroidContinuousVoiceGateway(service)
        gateway.setEnabled(true)
        val foreground = rejectForegroundPromotion(
            service, SecurityException("RECORD_AUDIO revoked during foreground promotion")
        )

        try {
            controller.create()

            org.mockito.Mockito.verify(foreground).ensureForeground()
            kotlin.test.assertFalse(gateway.isEnabled())
            val advisor = UltraWakeService::class.java.getDeclaredField("aiAdvisorLazy")
                .apply { isAccessible = true }
                .get(service) as Lazy<*>
            kotlin.test.assertFalse(advisor.isInitialized())
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun rejectedForegroundMicOnStartCommandDisablesStoredListeningAndReturnsNotSticky() {
        val controller = org.robolectric.Robolectric.buildService(UltraWakeService::class.java)
        val service = controller.get()
        val gateway = AndroidContinuousVoiceGateway(service)
        gateway.setEnabled(true)
        val foreground = rejectForegroundPromotion(
            service, IllegalStateException("Mic foreground service prohibited in background")
        )
        val start = android.content.Intent(service, UltraWakeService::class.java)
            .setAction(UltraWakeService.ACTION_START)

        try {
            assertEquals(android.app.Service.START_NOT_STICKY,
                service.onStartCommand(start, 0, 1))
            org.mockito.Mockito.verify(foreground).ensureForeground()
            kotlin.test.assertFalse(gateway.isEnabled())
        } finally {
            controller.destroy()
        }
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
