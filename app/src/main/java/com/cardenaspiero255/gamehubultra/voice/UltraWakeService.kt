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
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.cardenaspiero255.gamehubultra.GameLibrary
import com.cardenaspiero255.gamehubultra.GameSelectionStore
import com.cardenaspiero255.gamehubultra.ProfileSelectionStore
import com.cardenaspiero255.gamehubultra.ai.AiAdviceFormatter
import com.cardenaspiero255.gamehubultra.ai.GameHubAiAdvisor
import com.cardenaspiero255.gamehubultra.ai.GameHubAiContext
import com.cardenaspiero255.gamehubultra.ai.GeminiNanoLocalAiModelAdapter
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.platform.DeviceCapabilitiesProvider
import com.cardenaspiero255.gamehubultra.platform.DeviceInfoProvider
import com.cardenaspiero255.gamehubultra.platform.RuntimeDiagnosticsProvider
import java.util.Locale
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
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var stopped = false
    private var lastTranscriptAt = 0L
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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
        if (stopped) return

        if (
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            scheduleRestart()
            return
        }

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
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                    700L
                )
                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                    400L
                )
            }

            speech.startListening(intent)
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
            val transcript = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull { it.isNotBlank() }
                .orEmpty()

            if (transcript.isNotBlank()) {
                val now = System.currentTimeMillis()
                if (now - lastTranscriptAt > 900L) {
                    lastTranscriptAt = now
                    if (VoiceCommandParser.normalize(transcript).contains("ultra")) {
                        handleCommand(transcript)
                    }
                }
            }

            scheduleRestart()
        }

        override fun onError(error: Int) {
            scheduleRestart()
        }
    }

    private fun handleCommand(transcript: String) {
        Thread {
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

            val result = VoiceCommandEngine.execute(
                command = VoiceCommandParser.parse(transcript, aiAdvisor.intentResolver()),
                gamesProvider = { GameLibrary.discover(context).games },
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
                statusProvider = {
                    VoiceDeviceStatus(
                        batteryPercent = diagnostics.battery.percent,
                        thermalLabel = diagnostics.thermal.status?.toString() ?: "No disponible"
                    )
                },
                deferProfileApplication = true,
                aiAdvisor = { question -> aiAdvisor.advise(question, aiContext) }
            )

            val response = when (result) {
                is VoiceActionResult.ProfileSelected ->
                    "Perfil ${result.profile.title} seleccionado."
                is VoiceActionResult.GameOpened ->
                    "Abriendo ${result.game.label}."
                is VoiceActionResult.DeviceStatus ->
                    "Batería ${result.status.batteryPercent ?: "no disponible"} por ciento."
                is VoiceActionResult.AiAdvice ->
                    AiAdviceFormatter.fullResponse(context, result.advice)
                VoiceActionResult.Help ->
                    "Puedes decir: Ultra, abre un juego. Ultra, pon X4. Ultra, FPS balanceado."
                is VoiceActionResult.NotAvailable ->
                    "No disponible. ${result.detail}"
                VoiceActionResult.RequiresPermission ->
                    "Necesito permiso de micrófono."
                is VoiceActionResult.Failed ->
                    "No pude completar el comando. ${result.detail}"
            }

            mainHandler.post {
                tts?.speak(response, TextToSpeech.QUEUE_FLUSH, null, "ultra-command")
            }
        }.start()
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
        mainHandler.removeCallbacksAndMessages(null)
        if (!stopped) {
            mainHandler.postDelayed({ startRecognition() }, 300L)
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
