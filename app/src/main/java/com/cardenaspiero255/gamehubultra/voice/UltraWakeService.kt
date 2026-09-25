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
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.cardenaspiero255.gamehubultra.GameLibrary
import com.cardenaspiero255.gamehubultra.GameSelectionStore
import com.cardenaspiero255.gamehubultra.ProfileSelectionStore
import com.cardenaspiero255.gamehubultra.ai.AiAdviceFormatter
import com.cardenaspiero255.gamehubultra.ai.GameHubAiAdvisor
import com.cardenaspiero255.gamehubultra.ai.GameHubAiContext
import com.cardenaspiero255.gamehubultra.ai.GeminiNanoLocalAiModelAdapter
import com.cardenaspiero255.gamehubultra.ai.UltraAgentRoute
import com.cardenaspiero255.gamehubultra.ai.UltraRuntimeTelemetry
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
        private const val CHANNEL_ID = "ultra_voice"
        private const val NOTIFICATION_ID = 2301
        private const val RESTART_DELAY_MS = 180L
        private const val WAKE_DEBOUNCE_MS = 700L
        private const val COMMAND_UTTERANCE_ID = "ultra-command"
        private const val COMMAND_SPEECH_TIMEOUT_MS = 10_000L
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val commandCoordinator = UltraWakeCommandCoordinator()
    private val commandExecutor = Executors.newSingleThreadExecutor()
    private val restartRecognition = Runnable { startRecognition() }
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var stopped = false
    private var lastTranscriptAt = 0L
    private var recognitionStarting = false
    private val aiAdvisor by lazy { GameHubAiAdvisor(GeminiNanoLocalAiModelAdapter()) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        ensureForeground()
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
            }
        }
        tts?.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) {
                    if (utteranceId == COMMAND_UTTERANCE_ID) {
                        mainHandler.post { finishCommandAndResume() }
                    }
                }

                @Deprecated("Android legacy TextToSpeech callback")
                override fun onError(utteranceId: String?) {
                    if (utteranceId == COMMAND_UTTERANCE_ID) {
                        mainHandler.post { finishCommandAndResume() }
                    }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    if (utteranceId == COMMAND_UTTERANCE_ID) {
                        mainHandler.post { finishCommandAndResume() }
                    }
                }
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopSelf()
            ACTION_START, null -> {
                stopped = false
                ensureForeground()
                mainHandler.post { startRecognition() }
            }
        }
        return START_NOT_STICKY
    }

    private fun ensureForeground() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.cardenaspiero255.gamehubultra.R.drawable.ic_gamehub_tile)
            .setContentTitle("GameHub Ultra")
            .setContentText("Escucha activa: di “Ultra …” para usar comandos.")
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
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
        val started = runCatching {
            recognizer?.cancel()
            recognizer?.destroy()
            recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { speech ->
                speech.setRecognitionListener(listener)

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(
                        RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                        1200L
                    )
                    putExtra(
                        RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                        850L
                    )
                }

                speech.startListening(intent)
            }
            true
        }.getOrDefault(false)
        recognitionStarting = false

        if (!started) {
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

        override fun onResults(results: Bundle?) {
            commandCoordinator.onRecognitionFinished()
            val alternatives = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                .orEmpty()

            val transcript = alternatives
                .firstOrNull(::containsWakeWord)
                ?: alternatives.firstOrNull { it.isNotBlank() }
                .orEmpty()

            var commandStarted = false
            if (transcript.isNotBlank()) {
                val now = System.currentTimeMillis()
                if (
                    now - lastTranscriptAt > WAKE_DEBOUNCE_MS &&
                    containsWakeWord(transcript) &&
                    commandCoordinator.tryBeginCommand()
                ) {
                    lastTranscriptAt = now
                    commandStarted = true
                    handleCommand(transcript)
                }
            }

            if (!commandStarted) {
                scheduleRestart()
            }
        }

        override fun onError(error: Int) {
            commandCoordinator.onRecognitionFinished()
            scheduleRestart()
        }
    }

    private fun containsWakeWord(transcript: String): Boolean {
        val normalized = VoiceCommandParser.normalize(transcript)
        return normalized.split(" ").any { token ->
            token == "ultra" || (token.length >= 4 && levenshtein(token, "ultra") <= 1)
        }
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in a.indices) {
            current[0] = i + 1
            for (j in b.indices) {
                val cost = if (a[i] == b[j]) 0 else 1
                current[j + 1] = minOf(current[j] + 1, previous[j + 1] + 1, previous[j] + cost)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }

    private fun handleCommand(transcript: String) {
        commandExecutor.execute {
            val context = applicationContext
            val selectedGamePackage = runCatching {
                runBlocking { GameSelectionStore.selectedGameFlow(context).first() }
            }.getOrNull()

            val selectedProfile = runCatching {
                runBlocking { ProfileSelectionStore.selectedProfileFlow(context).first() }
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
            val route = UltraUnifiedAgentRouter.route(
                transcript = transcript,
                optionalResolver = aiAdvisor.intentResolver(),
                telemetry = UltraRuntimeTelemetry(
                    batteryPercent = status.batteryPercent,
                    thermalLabel = status.thermalLabel,
                    refreshRateHz = diagnostics.refresh.currentRefreshRateHz
                )
            )

            val response = when (route) {
                is UltraAgentRoute.Utility -> route.answer.message
                is UltraAgentRoute.Chat ->
                    aiAdvisor.chat(
                        message = route.message,
                        context = aiContext
                    )
                is UltraAgentRoute.Command -> {
                    val result = VoiceCommandEngine.execute(
                        command = route.command,
                        gamesProvider = { GameLibrary.discoverForVoice(context) },
                        launchGame = { packageName ->
                            launchGameFromService(context, packageName)
                        },
                        saveSelectedGame = { packageName ->
                            GameSelectionStore.saveSelectedGame(context, packageName)
                        },
                        saveSelectedProfile = { profile ->
                            ProfileSelectionStore.saveSelectedProfile(context, profile)
                        },
                        saveSelectedGameWithProfile = { packageName, profile ->
                            GameSelectionStore.saveSelectedGameAndProfile(context, packageName, profile)
                        },
                        isProfileAvailable = { profile ->
                            profile != PerformanceProfile.X4 ||
                                DeviceCapabilitiesProvider.get(context).sustainedPerformanceSupported
                        },
                        statusProvider = { status },
                        deferProfileApplication = true,
                        aiAdvisor = { question -> aiAdvisor.advise(question, aiContext) }
                    )

                    when (result) {
                        is VoiceActionResult.ProfileSelected ->
                            "Perfil ${result.profile.title} seleccionado."
                        is VoiceActionResult.GameOpened ->
                            "Abriendo ${result.game.label}."
                        is VoiceActionResult.DeviceStatus ->
                            "Estado: batería ${result.status.batteryPercent ?: "no disponible"} por ciento, térmica ${result.status.thermalLabel}."
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

            mainHandler.post {
                if (stopped) {
                    commandCoordinator.finishCommand()
                } else {
                    speakAndResume(response)
                }
            }
        }
    }

    private fun speakAndResume(response: String) {
        val result = tts?.speak(
            response,
            TextToSpeech.QUEUE_FLUSH,
            null,
            COMMAND_UTTERANCE_ID
        ) ?: TextToSpeech.ERROR

        if (result == TextToSpeech.ERROR) {
            finishCommandAndResume()
            return
        }

        mainHandler.postDelayed(
            { finishCommandAndResume() },
            COMMAND_SPEECH_TIMEOUT_MS
        )
    }

    private fun finishCommandAndResume() {
        if (!commandCoordinator.finishCommand()) return
        scheduleRestart()
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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Asistente de voz GameHub Ultra",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Indica que la escucha continua está activa."
            }

            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopped = true
        mainHandler.removeCallbacksAndMessages(null)
        commandExecutor.shutdownNow()
        commandCoordinator.onRecognitionFinished()
        commandCoordinator.finishCommand()
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        aiAdvisor.close()
        super.onDestroy()
    }
}
