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
import android.speech.RecognizerIntent
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

            val intent = baseRecognitionIntent()
            if (requestedMode == UltraWakeRecognitionMode.PERSISTENT_SEGMENTED) {
                val source = UltraPersistentSpeechSource.create()
                if (source == null) {
                    sessionPolicy.onPersistentSessionFailure()
                    requestedMode = UltraWakeRecognitionMode.LEGACY_RESTARTING
                } else {
                    persistentSpeechSource = source
                    persistentSessionActive = true
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.putExtra(
                            RecognizerIntent.EXTRA_AUDIO_SOURCE,
                            source.readDescriptor
                        )
                        intent.putExtra(
                            RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT,
                            UltraPersistentSpeechSource.CHANNEL_COUNT
                        )
                        intent.putExtra(
                            RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING,
                            UltraPersistentSpeechSource.ENCODING
                        )
                        intent.putExtra(
                            RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE,
                            UltraPersistentSpeechSource.SAMPLE_RATE_HZ
                        )
                        intent.putExtra(
                            RecognizerIntent.EXTRA_SEGMENTED_SESSION,
                            RecognizerIntent.EXTRA_AUDIO_SOURCE
                        )
                        intent.putStringArrayListExtra(
                            RecognizerIntent.EXTRA_BIASING_STRINGS,
                            arrayListOf("Ultra", "ultra")
                        )
                    }
                }
            }

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

    private fun baseRecognitionIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, recognitionLanguageTag)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            if (sessionPolicy.preferredMode() == UltraWakeRecognitionMode.LEGACY_RESTARTING) {
                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                    1200L
                )
                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                    850L
                )
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
                val context = applicationContext
                val selectionRepository: GameSelectionStateRepository =
                    GameHubProductionComposition.selectionRepository(context.applicationContext)
                val selectedGamePackage = runCatching {
                    runBlocking { selectionRepository.selectedGameFlow().first() }
                }.getOrNull()

                val selectedProfile = runCatching {
                    runBlocking {
                        selectionRepository.effectiveProfileForSelection(selectedGamePackage)
                    }
                }.getOrNull() ?: PerformanceProfile.BALANCED

            val device = DeviceInfoProvider.get(context)
            val diagnostics = RuntimeDiagnosticsProvider.get(context)
            val capabilities = DeviceCapabilitiesProvider.get(context)
            val aiContext = GameHubAiContext(
                selectedGamePackage = selectedGamePackage,
                sustainedPerformanceSupported = capabilities.sustainedPerformanceSupported,
                cpuCores = device.cpuCores,
                totalRamMb = device.totalRamMb.toInt(),
                gpuAvailable = !device.gpuRenderer.isNullOrBlank() ||
                    !device.gpuVendor.isNullOrBlank(),
                thermalStatus = diagnostics.thermal.status,
                thermalHeadroom = diagnostics.thermal.headroom,
                batteryPercent = diagnostics.battery.percent,
                charging = diagnostics.battery.charging,
                refreshRateHz = diagnostics.refresh.currentRefreshRateHz,
                networkValidated = diagnostics.connectivity.validated,
                networkLatencyMs = diagnostics.connectivity.latencyMs,
                downstreamBandwidthKbps =
                    diagnostics.connectivity.downstreamBandwidthKbps?.toLong(),
                storageFreePercent = diagnostics.storage.freePercent,
                inputDeviceCount = diagnostics.inputDeviceCount,
                selectedProfile = selectedProfile,
                sessionActive = selectedGamePackage != null
            )

            val status = VoiceDeviceStatus(
                batteryPercent = diagnostics.battery.percent,
                thermalLabel = voiceThermalLabel(diagnostics.thermal.status)
            )
            val intentResolver = aiAdvisor.intentResolver()
            voiceConversationLedger.bindScope(selectedGamePackage)
            val conversationBefore = voiceConversationLedger.snapshot()
            val route = UltraUnifiedAgentRouter.route(
                transcript = transcript,
                optionalResolver = intentResolver,
                telemetry = UltraRuntimeTelemetry(
                    batteryPercent = status.batteryPercent,
                    thermalLabel = status.thermalLabel,
                    refreshRateHz = diagnostics.refresh.currentRefreshRateHz
                ),
                knownGameAliases = aliasRepository.aliases().keys,
                conversationHistory = conversationBefore
            )

                when (route) {
                is UltraAgentRoute.Utility -> {
                    if (
                        route.answer.intent is
                            com.cardenaspiero255.gamehubultra.ai.UltraUtilityIntent.NetworkGamingControl
                    ) {
                        com.cardenaspiero255.gamehubultra.ai.UltraNetworkGamingRuntimeController.execute(
                            intent = route.answer.intent,
                            applyCompetitive = {
                                com.cardenaspiero255.gamehubultra.network.NetworkRuntimeOptimizer.apply(
                                    context,
                                    com.cardenaspiero255.gamehubultra.network.NetworkGameProfile.COMPETITIVE
                                )
                            }
                        )
                    } else {
                        route.answer.message
                    }
                }
                is UltraAgentRoute.Chat -> {
                    val memoryCommand = UltraMemoryCommandParser.parse(route.message)
                    val answer = queryExecutor.answer(
                        route = route,
                        stableKnowledgeFallback = {
                            aiAdvisor.generalKnowledgeChatOrNull(
                                message = route.message,
                                context = aiContext,
                                conversation = conversationBefore
                            )
                        }
                    ) {
                        aiAdvisor.chat(
                            message = route.message,
                            context = aiContext,
                            conversation = conversationBefore
                        )
                    }
                    if (memoryCommand == null) {
                        val delta = voiceConversationLedger.record(
                            userMessage = route.message,
                            assistantMessage = answer
                        )
                        ultraMemoryStore.enqueueSyncConversation(
                            previous = delta.previous,
                            next = delta.next,
                            scope = UltraMemoryScope(
                                userId = "local",
                                gamePackage = selectedGamePackage
                            ),
                            timestampMillis = System.currentTimeMillis()
                        )
                    } else if (
                        UltraMemoryTurnPersistencePolicy.resetsConversationContext(memoryCommand)
                    ) {
                        voiceConversationLedger.clear()
                    }
                    answer
                }
                is UltraAgentRoute.Command -> {
                    val result = VoiceCommandEngine.execute(
                        command = route.command,
                        gamesProvider = { GameLibrary.discoverForVoice(context) },
                        aliasGamesProvider = { GameLibrary.discover(context).games },
                        launchGame = { packageName ->
                            launchGameFromService(context, packageName)
                        },
                        saveSelectedGame = { packageName ->
                            DurableSelectionMutationQueue.enqueue(
                                onFailure = ::reportSelectionPersistenceFailure
                            ) {
                                selectionRepository.saveSelectedGame(packageName)
                            }
                        },
                        saveSelectedProfile = { profile ->
                            DurableSelectionMutationQueue.enqueue(
                                onFailure = ::reportSelectionPersistenceFailure
                            ) {
                                selectionRepository.saveSelectedProfile(profile)
                            }
                        },
                        saveSelectedGameWithProfile = { packageName, profile ->
                            DurableSelectionMutationQueue.enqueue(
                                onFailure = ::reportSelectionPersistenceFailure
                            ) {
                                selectionRepository.saveSelectedGameAndProfile(packageName, profile)
                            }
                        },
                        // X4 remains selectable even when the OEM does not expose Android's
                        // Sustained Performance Mode; unsupported hardware hooks degrade safely.
                        isProfileAvailable = { _ -> true },
                        statusProvider = { status },
                        deferProfileApplication = true,
                        aiAdvisor = { question -> aiAdvisor.advise(question, aiContext) },
                        aliasIntentResolver = intentResolver,
                        gameAliasesProvider = { aliasRepository.aliases() },
                        saveGameAlias = { alias, packageName ->
    aliasRepository.save(alias, packageName)
},
networkStatusProvider = { VoiceNetworkSnapshotFactory.current(context) },
applyNetworkProfile = { profile ->
    com.cardenaspiero255.gamehubultra.network.NetworkRuntimeOptimizer.apply(context, profile)
}
                    )

                    when (result) {
                        is VoiceActionResult.ProfileSelected ->
                            "Perfil ${result.profile.title} seleccionado."
                        is VoiceActionResult.GameOpened ->
                            "Abriendo ${result.game.label}."
                        is VoiceActionResult.GameAliasSaved ->
                            context.getString(
                                R.string.voice_result_game_alias_saved,
                                result.alias.uppercase(),
                                result.game.label
                            )
                        is VoiceActionResult.DeviceStatus ->
                            "Estado: batería ${result.status.batteryPercent ?: "no disponible"} por ciento, térmica ${result.status.thermalLabel}."
                        is VoiceActionResult.NetworkReport ->
                            NetworkVoiceResponseText.format(result)
                        is VoiceActionResult.AiAdvice ->
                            AiAdviceFormatter.fullResponse(context, result.advice)
                        VoiceActionResult.Help ->
                            "Puedes decir: Ultra, dime la hora. Ultra, dime la temperatura. Ultra, dime los Hz. Ultra, abre un juego. Ultra, pon X4."
                        is VoiceActionResult.NotAvailable ->
                            "No disponible. ${result.detail}"
                        VoiceActionResult.RequiresPermission ->
                            "Necesito permiso de micrófono."
                        is VoiceActionResult.Failed ->
                            "No pude completar el comando. ${result.detail}"
                    }
                }
            }

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

    private fun voiceThermalLabel(status: Int?): String =
        when (status) {
            PowerManager.THERMAL_STATUS_NONE -> "Normal"
            PowerManager.THERMAL_STATUS_LIGHT -> "Leve"
            PowerManager.THERMAL_STATUS_MODERATE -> "Moderado"
            PowerManager.THERMAL_STATUS_SEVERE -> "Severo"
            PowerManager.THERMAL_STATUS_CRITICAL -> "Crítico"
            PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergencia"
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "Apagado térmico"
            null -> "No disponible"
            else -> "Desconocido"
        }

    private fun launchGameFromService(context: Context, packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return false

        return runCatching {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private fun scheduleRestart() {
        mainHandler.removeCallbacks(restartRecognition)
        if (!stopped && !commandCoordinator.isCommandRunning()) {
            mainHandler.postDelayed(restartRecognition, RESTART_DELAY_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun reportSelectionPersistenceFailure(error: Throwable) {
        Log.e(
            "UltraWakeService",
            "No se pudo persistir la selección o el perfil de voz.",
            error
        )
    }

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
