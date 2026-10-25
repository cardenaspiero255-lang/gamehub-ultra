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
import android.speech.SpeechRecognizer.RESULTS_RECOGNITION
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.cardenaspiero255.gamehubultra.GameLibrary
import com.cardenaspiero255.gamehubultra.data.GameSelectionStateRepository
import com.cardenaspiero255.gamehubultra.composition.GameHubProductionComposition
import com.cardenaspiero255.gamehubultra.data.SessionCoachSessionStore
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
        private const val SPEECH_INTERRUPT_LISTEN_DELAY_MS = 280L
        private const val MAX_SPEECH_INTERRUPT_RETRIES = 3
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val foregroundController by lazy { UltraWakeForegroundController(this) }
    private val commandCoordinator = UltraWakeCommandCoordinator()
    private val commandQueue = UltraWakeCommandQueue()
    private val voiceConversationLedger = UltraVoiceConversationLedger(maxEntries = 8)
    private val speechGeneration = UltraWakeSpeechGeneration()
    private val playbackGuard = UltraWakePlaybackGuard()
    private val speechInitGate = UltraTtsInitializationGate()
    private var speechInterruptionRetryCount = 0
    private var waitingForSpeechEngine = false
    private var speechEngineFailed = false
    private val repeatGate = UltraWakeRepeatGate()
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
    private val recoveryPolicy = UltraWakeRecoveryPolicy()
    private val commandExecutor = Executors.newSingleThreadExecutor()
    private val queryExecutor: UltraQueryExecutor = UltraProductionQueryExecutor
    private val aliasRepository by lazy {
        GameHubProductionComposition.aliasRepository(applicationContext)
    }
    private val restartRecognition = Runnable { startRecognition() }
    private val listenDuringSpeech = Runnable {
        if (!stopped && playbackGuard.isPlaybackActive() &&
            commandCoordinator.isCommandRunning() && !persistentSessionActive
        ) {
            startRecognition(interruptionOnly = true)
        }
    }
    // A new generation is bound to every listening attempt. Late callbacks from
    // destroyed/replaced recognizers must not mutate the current session.
    private var recognitionGeneration = 0L
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
    private val aiAdvisorLazy = lazy {
        GameHubAiAdvisor(
            modelAdapter = GeminiNanoLocalAiModelAdapter(),
            preferFastStableDefinitions = true,
            memoryGateway = ultraMemoryStore,
            gameSessionHistory = {
                    SessionCoachSessionStore(applicationContext).readRecentGameSessions()
                },
                gamingSessionTelemetry = {
                    SessionCoachSessionStore(applicationContext).readActiveGamingMeasurementWindow()
                }
        )
    }
    private val aiAdvisor by aiAdvisorLazy

    override fun onCreate() {
        super.onCreate()
        GameHubProductionComposition.configureUltraResearchPersistence(
            applicationContext
        )
        if (!UltraWakeForegroundSafety.tryStart(
                foregroundController::ensureForeground, this::stopSelf,
                disableContinuousVoice = {
                    AndroidContinuousVoiceGateway(this).setEnabled(false)
                }
            )
        ) return
        tts = TextToSpeech(this) { status ->
            // The engine may initialize before the constructor assigns tts.
            mainHandler.post { handleTtsInitialization(status) }
        }
        tts?.let(speechPlayback::attach)
    }

    private fun handleTtsInitialization(status: Int) {
        if (stopped || !lifecycleGate.canAcceptRecognition()) return
        val ready = status == TextToSpeech.SUCCESS
        if (ready) tts?.let(UltraSpeechLocalePolicy::applyTo)
        val pending = speechInitGate.initialized(ready)
        speechEngineFailed = !ready
        if (pending != null && waitingForSpeechEngine) {
            waitingForSpeechEngine = false
            speakWithInterruption(pending)
        } else if (!ready && waitingForSpeechEngine) {
            // No TTS is available. Complete this turn so wake recognition
            // never remains stuck waiting for a speech callback.
            waitingForSpeechEngine = false
            speechPlayback.speak("")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopSelf()
            ACTION_START, null -> {
                stopped = false
                lifecycleGate.restart()
                if (!UltraWakeForegroundSafety.tryStart(
                        foregroundController::ensureForeground, this::stopSelf,
                        disableContinuousVoice = {
                            AndroidContinuousVoiceGateway(this).setEnabled(false)
                        }
                    )
                ) return START_NOT_STICKY
                mainHandler.post { startRecognition() }
            }
        }
        return START_NOT_STICKY
    }

    // Preserve the no-argument entry point used by the existing Robolectric
    // recovery tests and ordinary service restart callbacks.
    private fun startRecognition() = startRecognition(interruptionOnly = false)

    private fun startRecognition(interruptionOnly: Boolean) {
        if (stopped || recognitionStarting) return
        val interruptWindow = interruptionOnly && playbackGuard.isPlaybackActive()
        if (interruptionOnly && !interruptWindow) return
        if (interruptWindow) {
            if (!commandCoordinator.tryStartSpeechInterruptionRecognition()) return
        } else if (!commandCoordinator.tryStartRecognition()) {
            return
        }

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
            scheduleRestart(recoveryPolicy.nextDelayMillis())
            return
        }

        recognitionStarting = true
        // A bounded legacy recognizer can listen for "Ultra, para" while
        // TTS is active if Android grants the mic. Persistent mode remains
        // reserved for the normal continuous-listening session.
        var requestedMode = if (interruptWindow) {
            UltraWakeRecognitionMode.LEGACY_RESTARTING
        } else {
            sessionPolicy.preferredMode()
        }
        val started = runCatching {
            val speech = recognizer ?: SpeechRecognizer.createSpeechRecognizer(this).also {
                recognizer = it
            }
            val sessionGeneration = ++recognitionGeneration
            speech.setRecognitionListener(listenerForGeneration(sessionGeneration, interruptWindow))

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
            recognitionGeneration++ // Invalidate queued callbacks before teardown.
            // A failed recognizer may be poisoned/busy. Rebuild it on the next
            // attempt rather than repeatedly restarting the same instance.
            runCatching { recognizer?.cancel() }
            runCatching { recognizer?.destroy() }
            recognizer = null
            if (requestedMode == UltraWakeRecognitionMode.PERSISTENT_SEGMENTED) {
                sessionPolicy.onPersistentSessionFailure()
            }
            closePersistentSpeechSource()
            commandCoordinator.onRecognitionFinished()
            scheduleRestart(recoveryPolicy.nextDelayMillis())
        }
    }

    private val listener = object : RecognitionListener {
        // Android can emit READY immediately before ERROR_RECOGNIZER_BUSY.
        // Readiness alone is not evidence that recognition recovered.
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onPartialResults(partialResults: Bundle?) {
            val alternatives = partialResults
                ?.getStringArrayList(RESULTS_RECOGNITION)
                .orEmpty()
            if (UltraWakePartialStopPolicy.shouldStop(
                    playbackActive = playbackGuard.isPlaybackActive(),
                    alternatives = alternatives
                )
            ) {
                if (cancelPendingSpeechAndResume()) return
                stopActiveUltraSpeech(speechPlayback::stop, speechGeneration::activeToken) {
                    finishCommandAndResume(it, playbackEnded = true)
                }
            }
        }
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onSegmentResults(segmentResults: Bundle) {
            if (persistentSessionActive) {
                recoveryPolicy.onRecognitionResult(segmentResults.getStringArrayList(RESULTS_RECOGNITION))
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
            recoveryPolicy.onRecognitionResult(results?.getStringArrayList(RESULTS_RECOGNITION))
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
            if (stopped || !lifecycleGate.canAcceptRecognition()) return
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
            if (UltraWakeRecognitionHealth.shouldRebuildRecognizer(error)) {
                recognitionGeneration++ // Reject delayed callbacks from this poisoned instance.
                runCatching { recognizer?.destroy() }
                recognizer = null
            }
            scheduleRestart(recoveryPolicy.nextDelayMillis())
        }
    }

    /**
     * SpeechRecognizer callbacks can arrive after destroy() or a new startListening().
     * Bind each registration to its session generation, not the mutable recognizer field.
     */
    private fun listenerForGeneration(generation: Long): RecognitionListener =
        listenerForGeneration(generation, interruptionOnly = false)

    private fun listenerForGeneration(
        generation: Long,
        interruptionOnly: Boolean
    ): RecognitionListener =
        object : RecognitionListener by listener {
            // Non-actionable callbacks are delegated to the existing no-op handler.
            // Every callback that changes recovery or session state is guarded below.
            private fun isCurrent() = generation == recognitionGeneration && !stopped

            override fun onPartialResults(partialResults: Bundle?) {
                if (isCurrent()) listener.onPartialResults(partialResults)
            }

            override fun onSegmentResults(segmentResults: Bundle) {
                if (isCurrent()) listener.onSegmentResults(segmentResults)
            }

            override fun onEndOfSegmentedSession() {
                if (isCurrent()) listener.onEndOfSegmentedSession()
            }

            override fun onResults(results: Bundle?) {
                if (!isCurrent()) return
                if (interruptionOnly) {
                    // Some recognizers provide stop words only in final results.
                    // Forward stop intent to the safe partial-stop handler, not command routing.
                    val alternatives = results?.getStringArrayList(RESULTS_RECOGNITION).orEmpty()
                    val stopRequested = UltraWakePartialStopPolicy.shouldStop(
                        playbackActive = playbackGuard.isPlaybackActive(),
                        alternatives = alternatives
                    )
                    if (stopRequested) {
                        listener.onPartialResults(results)
                    }
                    commandCoordinator.onRecognitionFinished()
                    // A background phrase must not permanently exhaust the STOP
                    // listening opportunity during a long spoken answer.
                    if (!stopRequested && playbackGuard.isPlaybackActive() && !stopped) {
                        scheduleSpeechInterruptionListenerAfterDelay(
                            SPEECH_INTERRUPT_LISTEN_DELAY_MS
                        )
                    }
                    return
                }
                listener.onResults(results)
            }

            override fun onError(error: Int) {
                if (!isCurrent()) return
                if (interruptionOnly) {
                    commandCoordinator.onRecognitionFinished()
                    speechInterruptionRetryCount++
                    if (speechInterruptionRetryCount < MAX_SPEECH_INTERRUPT_RETRIES &&
                        playbackGuard.isPlaybackActive()
                    ) {
                        scheduleSpeechInterruptionListenerAfterDelay(
                            SPEECH_INTERRUPT_LISTEN_DELAY_MS *
                                (1L shl speechInterruptionRetryCount)
                        )
                    }
                    return
                }
                listener.onError(error)
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

        val confidenceScores = results
            ?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)

        val transcript = UltraSpeechCandidateRanker.select(
            alternatives = alternatives,
            confidenceScores = confidenceScores,
            prefer = ::containsWakeWord
        ).orEmpty()

        when (
            UltraWakeBargeInPolicy.decide(
                playbackActive = playbackGuard.isPlaybackActive(),
                transcript = transcript,
                playbackEcho = playbackGuard.isLikelyPlaybackEcho(transcript)
            )
        ) {
            UltraWakeRecognitionDisposition.SUPPRESS -> return

            UltraWakeRecognitionDisposition.STOP_TTS -> {
                if (cancelPendingSpeechAndResume()) return
                return stopActiveUltraSpeech(speechPlayback::stop, speechGeneration::activeToken) {
                    finishCommandAndResume(it, playbackEnded = true)
                }
            }

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
            if (
                now - lastTranscriptAt > WAKE_DEBOUNCE_MS &&
                repeatGate.shouldAccept(transcript, now)
            ) {
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
        val privacyEpoch = UltraPrivacyEpoch.current()
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
                    val response =
                        if (UltraPrivacyEpoch.isCurrent(privacyEpoch)) {
                            UltraWakeFailureGuard.run { commandRuntime.execute(transcript) }
                        } else ""
                    mainHandler.post {
                        if (
                            stopped || !lifecycleGate.canAcceptRecognition() ||
                            !UltraPrivacyEpoch.isCurrent(privacyEpoch) || response.isBlank()
                        ) {
                            commandCoordinator.finishCommand()
                            if (!stopped) scheduleRestart()
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

    private fun speakAndResume(response: String) {
        speechInterruptionRetryCount = 0
        val readyText = speechInitGate.offer(response)
        when {
            readyText != null -> {
                speakWithInterruption(readyText)
            }
            speechEngineFailed -> speechPlayback.speak("")
            response.isNotBlank() -> waitingForSpeechEngine = true
            else -> speechPlayback.speak("")
        }
    }

    /**
     * Persistent segmented recognition owns an AudioRecord and keeps its slot
     * marked active while a command runs. Before audible TTS, hand that mic
     * back to Android so a *separate, stop-only* recognizer can hear the user.
     * Do not call onPersistentSessionFailure(): this is an intentional handoff.
     */
    private fun retirePersistentCaptureBeforeSpeaking() {
        if (!persistentSessionActive || !commandCoordinator.isCommandRunning()) return
        recognitionGeneration++ // Ignore late callbacks from the old segmented session.
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
        closePersistentSpeechSource()
        commandCoordinator.onRecognitionFinished()
    }

    private fun speakWithInterruption(response: String) {
        retirePersistentCaptureBeforeSpeaking()
        speechPlayback.speak(response)
        scheduleSpeechInterruptionListener()
    }

    // Keep the no-argument entry point for existing callers and Robolectric tests.
    private fun scheduleSpeechInterruptionListener() =
        scheduleSpeechInterruptionListenerAfterDelay(SPEECH_INTERRUPT_LISTEN_DELAY_MS)

    // Distinctly named delayed scheduling avoids confusing this overload
    // with recursive recognition restarts during static security review.
    private fun scheduleSpeechInterruptionListenerAfterDelay(delayMillis: Long) {
        // This is best-effort on Android SpeechRecognizer: some OEMs do not
        // permit input while TextToSpeech is speaking. It is only enabled after
        // explicit consent to continuous listening and while TTS is active.
        mainHandler.removeCallbacks(listenDuringSpeech)
        if (playbackGuard.isPlaybackActive() && !persistentSessionActive &&
            speechInterruptionRetryCount < MAX_SPEECH_INTERRUPT_RETRIES
        ) {
            mainHandler.postDelayed(listenDuringSpeech, delayMillis)
        }
    }

    /**
     * The recognizer can hear "Ultra, para" before the TTS engine is ready.
     * No speech generation token exists yet in that state, so release the
     * pending answer explicitly and finish its command exactly once.
     */
    private fun cancelPendingSpeechAndResume(): Boolean {
        if (!waitingForSpeechEngine) return false
        waitingForSpeechEngine = false
        speechInitGate.cancelPending()
        finishCommandAndResume(speechGeneration.begin(), playbackEnded = false)
        return true
    }

    private fun finishCommandAndResume(
        token: Long,
        playbackEnded: Boolean
    ) {
        if (!speechGeneration.complete(token)) return
        mainHandler.removeCallbacks(listenDuringSpeech)
        if (playbackEnded) {
            playbackGuard.onPlaybackFinished(System.currentTimeMillis())
            speechInterruptionRetryCount = 0
        }
        if (commandCoordinator.isRecognitionActive() && !persistentSessionActive) {
            recognitionGeneration++
            runCatching { recognizer?.cancel() }
            commandCoordinator.onRecognitionFinished()
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

    private fun scheduleRestart(delayMillis: Long = RESTART_DELAY_MS) {
        mainHandler.removeCallbacks(restartRecognition)
        if (!stopped && !commandCoordinator.isCommandRunning()) {
            mainHandler.postDelayed(restartRecognition, delayMillis)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopped = true
        recognitionGeneration++ // Retire any callbacks already queued before destruction.
        waitingForSpeechEngine = false
        speechInitGate.close()
        lifecycleGate.stop()
        mainHandler.removeCallbacksAndMessages(null)
        commandExecutor.shutdownNow()
        commandQueue.clear()
        voiceConversationLedger.clear()
        speechGeneration.clear()
        playbackGuard.clear()
        repeatGate.clear()
        closePersistentSpeechSource()
        commandCoordinator.onRecognitionFinished()
        commandCoordinator.finishCommand()
        // Android SpeechRecognizer can throw after the microphone service
        // is revoked or the recognizer was invalidated by the system.
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
        speechPlayback.shutdown()
        tts = null
        // A denied foreground microphone startup may stop the service before
        // any AI command ran. Do not initialize GeminiNano solely to close it.
        if (aiAdvisorLazy.isInitialized()) {
            aiAdvisor.close()
        }
        super.onDestroy()
    }
}
