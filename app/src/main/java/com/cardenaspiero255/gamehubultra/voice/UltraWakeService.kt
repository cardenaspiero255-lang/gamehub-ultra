package com.cardenaspiero255.gamehubultra.voice

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.cardenaspiero255.gamehubultra.GameLibrary
import com.cardenaspiero255.gamehubultra.data.GameSelectionStateRepository
import com.cardenaspiero255.gamehubultra.composition.GameHubProductionComposition
import com.cardenaspiero255.gamehubultra.data.DurableSelectionMutationQueue
import com.cardenaspiero255.gamehubultra.data.effectiveProfileForSelection
import com.cardenaspiero255.gamehubultra.R
import com.cardenaspiero255.gamehubultra.ai.AiAdviceFormatter
import com.cardenaspiero255.gamehubultra.ai.GameHubAiAdvisor
import com.cardenaspiero255.gamehubultra.ai.GameHubAiContext
import com.cardenaspiero255.gamehubultra.ai.GeminiNanoLocalAiModelAdapter
import com.cardenaspiero255.gamehubultra.ai.UltraAgentRoute
import com.cardenaspiero255.gamehubultra.ai.UltraProductionQueryExecutor
import com.cardenaspiero255.gamehubultra.ai.UltraQueryExecutor
import com.cardenaspiero255.gamehubultra.ai.UltraRuntimeTelemetry
import com.cardenaspiero255.gamehubultra.ai.UltraMemoryScope
import com.cardenaspiero255.gamehubultra.ai.UltraMemoryCommandParser
import com.cardenaspiero255.gamehubultra.ai.UltraMemoryTurnPersistencePolicy
import com.cardenaspiero255.gamehubultra.ai.UltraUnifiedAgentRouter
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.platform.DeviceCapabilitiesProvider
import com.cardenaspiero255.gamehubultra.platform.DeviceInfoProvider
import com.cardenaspiero255.gamehubultra.platform.RuntimeDiagnosticsProvider
import java.util.Locale
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Opt-in continuous wake listener.
 * The microphone stays active only after the user explicitly enables the
 * feature. Recognition is ignored unless the transcript contains "Ultra".
 */
class UltraWakeService : Service() {
    companion object {
        const val ACTION_START = "com.cardenaspiero255.gamehubultra.voice.START"
        const val ACTION_STOP = "com.cardenaspiero255.gamehubultra.voice.STOP"
        private const val RESTART_DELAY_MS = 180L
        private const val WAKE_DEBOUNCE_MS = 700L
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val foregroundController by lazy { UltraWakeForegroundController(this) }
    private val commandCoordinator = UltraWakeCommandCoordinator()
    private val commandQueue = UltraWakeCommandQueue()
    private val voiceConversationLedger = UltraVoiceConversationLedger(maxEntries = 8)
    private val speechGeneration = UltraWakeSpeechGeneration()
    private val playbackGuard = UltraWakePlaybackGuard()
    private val lifecycleGate = UltraWakeLifecycleGate()
    private val speechPlayback by lazy {
        UltraWakeSpeechPlaybackController(
            mainHandler = mainHandler,
            speechGeneration = speechGeneration,
            playbackGuard = playbackGuard,
            onPlaybackFinished = { token -> finishCommandAndResume(token, playbackEnded = true) }
        )
    }
    private val sessionPolicy = UltraWakeSessionPolicy(Build.VERSION.SDK_INT)
    private val commandExecutor = Executors.newSingleThreadExecutor()
    private val queryExecutor: UltraQueryExecutor = UltraProductionQueryExecutor
    private val aliasRepository by lazy {
        GameHubProductionComposition.aliasRepository(applicationContext)
    }
    private val restartRecognition = Runnable { startRecognition() }
    private var recognizer: SpeechRecognizer? = null
    private var persistentSpeechSource: UltraPersistentSpeechSource? = null
    private var persistentSessionActive = false
    private var tts: TextToSpeech? = null
    private var stopped = false
    private var lastTranscriptAt = 0L
    private var recognitionStarting = false
    private var recognitionLanguageTag = UltraSpeechLocalePolicy.PREFERRED_TAG
    private val ultraMemoryStore by lazy {
        GameHubProductionComposition.ultraConversationMemory(applicationContext)
    }
    private val aiAdvisor by lazy {
        GameHubAiAdvisor(
            modelAdapter = GeminiNanoLocalAiModelAdapter(),
            memoryGateway = ultraMemoryStore
        )
    }

    override fun onCreate() {
        super.onCreate()
        foregroundController.ensureForeground()
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.let(UltraSpeechLocalePolicy::applyTo)
            }
        }
        tts?.let(speechPlayback::attach)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopSelf()
            ACTION_START, null -> {
                stopped = false
                lifecycleGate.restart()
                foregroundController.ensureForeground()
                mainHandler.post { startRecognition() }
            }
        }
        return START_NOT_STICKY
    }

    private fun startRecognition() {
        if (stopped || recognitionStarting) return
        if (!commandCoordinator.tryStartRecognition()) return

        if (
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            commandCoordinator.onRecognitionFinished()
            stopSelf()
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            commandCoordinator.onRecognitionFinished()
            scheduleRestart()
            return
        }

        recognitionStarting = true
        var requestedMode = sessionPolicy.preferredMode()
        val started = runCatching {
            val speech = recognizer ?: SpeechRecognizer.createSpeechRecognizer(this).also {
                it.setRecognitionListener(listener)
                recognizer = it
            }

            if (requestedMode == UltraWakeRecognitionMode.PERSISTENT_SEGMENTED) {
                val source = UltraPersistentSpeechSource.create()
                if (source == null) {
                    sessionPolicy.onPersistentSessionFailure()
                    requestedMode = UltraWakeRecognitionMode.LEGACY_RESTARTING
                } else {
                    persistentSpeechSource = source
                    persistentSessionActive = true
                }
            }

            val intent = UltraWakeRecognitionIntentFactory.create(
                languageTag = recognitionLanguageTag,
                mode = requestedMode,
                persistentSource = persistentSpeechSource
            )
            speech.startListening(intent)
            if (
                requestedMode == UltraWakeRecognitionMode.PERSISTENT_SEGMENTED &&
                persistentSpeechSource?.start() != true
            ) {
                error("Persistent microphone source could not start")
            }
            true
        }.getOrDefault(false)
        recognitionStarting = false

        if (!started) {
            recognizer?.cancel()
            if (requestedMode == UltraWakeRecognitionMode.PERSISTENT_SEGMENTED) {
                sessionPolicy.onPersistentSessionFailure()
            }
            closePersistentSpeechSource()
            commandCoordinator.onRecognitionFinished()
            scheduleRestart()
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onPartialResults(partialResults: Bundle?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onSegmentResults(segmentResults: Bundle) {
            if (persistentSessionActive) {
                handleRecognitionBundle(
                    results = segmentResults,
                    keepRecognitionActive = true
                )
            }
        }

        override fun onEndOfSegmentedSession() {
            if (!persistentSessionActive) return
            sessionPolicy.onPersistentSessionFailure()
            closePersistentSpeechSource()
            commandCoordinator.onRecognitionFinished()
            if (!stopped && !commandCoordinator.isCommandRunning()) {
                scheduleRestart()
            }
        }

        override fun onResults(results: Bundle?) {
            val wasPersistent = persistentSessionActive
            if (wasPersistent) {
                sessionPolicy.onPersistentSessionFailure()
                closePersistentSpeechSource()
            }
            commandCoordinator.onRecognitionFinished()
            handleRecognitionBundle(
                results = results,
                keepRecognitionActive = false
            )
        }

        override fun onError(error: Int) {
            val fallback = UltraSpeechLocalePolicy.fallbackRecognitionTag(
                error = error,
                currentTag = recognitionLanguageTag
            )
            if (fallback != null) {
                recognitionLanguageTag = fallback
            }
            if (persistentSessionActive) {
                sessionPolicy.onPersistentSessionFailure()
                closePersistentSpeechSource()
            }
            commandCoordinator.onRecognitionFinished()
            scheduleRestart()
        }
    }

    private fun handleRecognitionBundle(
        results: Bundle?,
        keepRecognitionActive: Boolean
    ) {
        if (!lifecycleGate.canAcceptRecognition()) return
        val recognitionNow = System.currentTimeMillis()

        val alternatives = results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            .orEmpty()

        val transcript = alternatives
            .firstOrNull(::containsWakeWord)
            ?: alternatives.firstOrNull { it.isNotBlank() }
            .orEmpty()

        when (
            UltraWakeBargeInPolicy.decide(
                playbackActive = playbackGuard.isPlaybackActive(),
                transcript = transcript,
                playbackEcho = playbackGuard.isLikelyPlaybackEcho(transcript)
            )
        ) {
            UltraWakeRecognitionDisposition.SUPPRESS -> return

            UltraWakeRecognitionDisposition.INTERRUPT_TTS -> {
                val now = System.currentTimeMillis()
                if (
                    transcript.isBlank() ||
                    now - lastTranscriptAt <= WAKE_DEBOUNCE_MS
                ) {
                    return
                }
                lastTranscriptAt = now
                commandQueue.offer(transcript)
                speechPlayback.stop()
                speechGeneration.activeToken()?.let { token ->
                    finishCommandAndResume(token, playbackEnded = true)
                }
                return
            }

            UltraWakeRecognitionDisposition.ACCEPT -> {
                // Keep the post-TTS drain window so the tail of Ultra's own
                // audio cannot immediately trigger another recognition turn.
                if (playbackGuard.shouldSuppressRecognition(recognitionNow)) return
            }
        }

        var commandStarted = false
        if (transcript.isNotBlank() && containsWakeWord(transcript)) {
            val now = System.currentTimeMillis()
            if (now - lastTranscriptAt > WAKE_DEBOUNCE_MS) {
                lastTranscriptAt = now
                commandStarted = commandCoordinator.tryBeginCommand(
                    keepRecognitionActive = keepRecognitionActive
                )
                if (commandStarted) {
                    handleCommand(transcript)
                } else if (commandCoordinator.isCommandRunning()) {
                    commandQueue.offer(transcript)
                }
            }
        }

        if (!commandStarted && !keepRecognitionActive) {
            scheduleRestart()
        }
    }

    private fun containsWakeWord(transcript: String): Boolean =
        UltraWakeWordMatcher.contains(transcript)

    private val commandRuntime by lazy {
        UltraWakeCommandRuntime(
            context = applicationContext,
            queryExecutor = queryExecutor,
            aiAdvisor = aiAdvisor,
            aliasRepository = aliasRepository,
            memoryStore = ultraMemoryStore,
            conversationLedger = voiceConversationLedger
        )
    }

    private fun handleCommand(transcript: String) {
        if (!lifecycleGate.canAcceptRecognition()) {
            commandCoordinator.finishCommand()
            return
        }

        val submitted = runCatching {
            commandExecutor.execute {
                UltraWakeCommandTaskGuard.run(
                    onUnposted = {
                        commandCoordinator.finishCommand()
                        scheduleRestart()
                    }
                ) {
                    val response = UltraWakeFailureGuard.run {
                        commandRuntime.execute(transcript)
                    }
                    mainHandler.post {
                        if (stopped || !lifecycleGate.canAcceptRecognition()) {
                            commandCoordinator.finishCommand()
                        } else {
                            speakAndResume(response)
                        }
                    }
                }
            }
            true
        }.getOrDefault(false)

        if (!submitted) {
            commandCoordinator.finishCommand()
        }
    }

    private fun speakAndResume(response: String) = speechPlayback.speak(response)

    private fun finishCommandAndResume(
        token: Long,
        playbackEnded: Boolean
    ) {
        if (!speechGeneration.complete(token)) return
        if (playbackEnded) {
            playbackGuard.onPlaybackFinished(System.currentTimeMillis())
        }
        if (!commandCoordinator.finishCommand()) return

        val queued = commandQueue.poll()
        if (queued != null) {
            val started = commandCoordinator.tryBeginCommand(
                keepRecognitionActive = persistentSessionActive
            )
            if (started) {
                handleCommand(queued)
                return
            }
        }

        if (!persistentSessionActive) {
            scheduleRestart()
        }
    }

    private fun closePersistentSpeechSource() {
        persistentSessionActive = false
        persistentSpeechSource?.close()
        persistentSpeechSource = null
    }

    private fun scheduleRestart() {
        mainHandler.removeCallbacks(restartRecognition)
        if (!stopped && !commandCoordinator.isCommandRunning()) {
            mainHandler.postDelayed(restartRecognition, RESTART_DELAY_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopped = true
        lifecycleGate.stop()
        mainHandler.removeCallbacksAndMessages(null)
        commandExecutor.shutdownNow()
        commandQueue.clear()
        voiceConversationLedger.clear()
        speechGeneration.clear()
        playbackGuard.clear()
        closePersistentSpeechSource()
        commandCoordinator.onRecognitionFinished()
        commandCoordinator.finishCommand()
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        speechPlayback.shutdown()
        tts = null
        aiAdvisor.close()
        super.onDestroy()
    }
}
