package com.cardenaspiero255.gamehubultra.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.TextView
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import androidx.core.content.ContextCompat
import com.cardenaspiero255.gamehubultra.GameLibrary
import com.cardenaspiero255.gamehubultra.GameSelectionStore
import com.cardenaspiero255.gamehubultra.ProfileSelectionStore
import com.cardenaspiero255.gamehubultra.R
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.ai.AiAdviceFormatter
import com.cardenaspiero255.gamehubultra.ai.GameHubAiAdvisor
import com.cardenaspiero255.gamehubultra.ai.GameHubAiContext
import com.cardenaspiero255.gamehubultra.ai.GeminiNanoLocalAiModelAdapter
import com.cardenaspiero255.gamehubultra.platform.DeviceCapabilitiesProvider
import com.cardenaspiero255.gamehubultra.platform.DeviceInfoProvider
import com.cardenaspiero255.gamehubultra.platform.RuntimeDiagnosticsProvider
import java.util.Locale
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class GameHubVoiceInteractionService : VoiceInteractionService()

class GameHubVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle): VoiceInteractionSession =
        GameHubVoiceInteractionSession(this)

    companion object {
        const val COMMAND_EXECUTE_TEXT =
            "com.cardenaspiero255.gamehubultra.EXECUTE_VOICE_COMMAND"
        const val EXTRA_TRANSCRIPT = "transcript"
        const val KEY_STATUS = "status"
        const val KEY_RESPONSE = "response"
    }
}

private class GameHubVoiceInteractionSession(context: Context) :
    VoiceInteractionSession(context) {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private val aiAdvisor = GameHubAiAdvisor(GeminiNanoLocalAiModelAdapter())

    override fun onCreateContentView(): View =
        TextView(getContext()).apply {
            text = "GameHub Ultra: escuchando un comando…"
            textSize = 18f
            setPadding(48, 48, 48, 48)
        }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        startOneShotRecognition()
    }

    override fun onHide() {
        stopRecognizer()
        super.onHide()
    }

    private fun startOneShotRecognition() {
        val context = getContext()
        val microphoneGranted =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

        if (!microphoneGranted) {
            speakAndFinish(
                "Se necesita permiso de micrófono. Abre GameHub Ultra y concédelo para usar el asistente."
            )
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            speakAndFinish("El reconocimiento de voz no está disponible en este dispositivo.")
            return
        }

        stopRecognizer()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).also { speech ->
            speech.setRecognitionListener(listener)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
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

        override fun onResults(results: Bundle?) {
            stopRecognizer()
            val transcript =
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull { it.isNotBlank() }
                    .orEmpty()

            if (transcript.isBlank()) {
                speakAndFinish("No recibí un comando de voz.")
                return
            }

            executor.execute { handleTranscript(transcript) }
        }

        override fun onPartialResults(partialResults: Bundle?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onError(error: Int) {
            stopRecognizer()
            speakAndFinish("No pude reconocer el comando de voz. Inténtalo de nuevo.")
        }
    }

    private fun handleTranscript(transcript: String) {
        val context = getContext()
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
            downstreamBandwidthKbps = diagnostics.connectivity.downstreamBandwidthKbps?.toLong(),
            storageFreePercent = diagnostics.storage.freePercent,
            inputDeviceCount = diagnostics.inputDeviceCount,
            selectedProfile = selectedProfile,
            sessionActive = selectedGamePackage != null
        )
        val intentResolver = aiAdvisor.intentResolver()
        val result = VoiceCommandEngine.execute(
            command = VoiceCommandParser.parse(
                transcript = transcript,
                optionalResolver = intentResolver,
                knownGameAliases = GameAliasStore.aliases(context).keys
            ),
            gamesProvider = { GameLibrary.discover(context).games },
            launchGame = { packageName -> launchGameFromVoice(packageName) },
            saveSelectedGame = { packageName ->
                GameSelectionStore.saveSelectedGame(context, packageName)
            },
            saveSelectedProfile = { profile ->
                ProfileSelectionStore.saveSelectedProfile(context, profile)
            },
            saveSelectedGameWithProfile = { packageName, profile ->
                GameSelectionStore.saveSelectedGameAndProfile(context, packageName, profile)
            },
            // X4 is selectable as a GameHub Ultra profile. Platform-only performance
            // hooks are enabled separately when the device reports support.
            isProfileAvailable = { _ -> true },
            statusProvider = { readStatus() },
            deferProfileApplication = true,
            aiAdvisor = { question -> aiAdvisor.advise(question, aiContext) },
            aliasIntentResolver = intentResolver,
            gameAliasesProvider = { GameAliasStore.aliases(context) },
            saveGameAlias = { alias, packageName ->
                GameAliasStore.save(context, alias, packageName)
            }
        )

        val response = responseText(result)
        mainHandler.post {
            when (result) {
                is VoiceActionResult.GameOpened -> {
                    speak(response)
                }
                else -> {
                    speakAndFinish(response)
                }
            }
        }
    }

    private fun launchGameFromVoice(packageName: String): Boolean {
        val intent = getContext().packageManager.getLaunchIntentForPackage(packageName)
            ?: return false

        return try {
            startVoiceActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun readStatus(): VoiceDeviceStatus {
        val batteryManager = getContext().getSystemService(BatteryManager::class.java)
        val powerManager = getContext().getSystemService(PowerManager::class.java)
        val battery = batteryManager?.getIntProperty(
            BatteryManager.BATTERY_PROPERTY_CAPACITY
        )?.takeIf { it in 0..100 }

        val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when (powerManager?.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> "Normal"
                PowerManager.THERMAL_STATUS_LIGHT -> "Leve"
                PowerManager.THERMAL_STATUS_MODERATE -> "Moderado"
                PowerManager.THERMAL_STATUS_SEVERE -> "Severo"
                PowerManager.THERMAL_STATUS_CRITICAL -> "Crítico"
                PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergencia"
                PowerManager.THERMAL_STATUS_SHUTDOWN -> "Apagado térmico"
                else -> "Desconocido"
            }
        } else {
            "No disponible"
        }

        return VoiceDeviceStatus(
            batteryPercent = battery,
            thermalLabel = thermal
        )
    }

    private fun responseText(result: VoiceActionResult): String =
        when (result) {
            is VoiceActionResult.ProfileSelected ->
                if (result.deferred) {
                    "Perfil " + result.profile.title +
                        " guardado. Se aplicará cuando GameHub Ultra esté activo."
                } else {
                    "Perfil " + result.profile.title + " seleccionado."
                }

            is VoiceActionResult.GameOpened -> {
                val base = "Abrí " + result.game.label + "."
                when {
                    result.profileDeferred ->
                        base + " Perfil " +
                            (result.profile?.title ?: "solicitado") +
                            " guardado para cuando GameHub Ultra esté activo."
                    result.profileUnavailable ->
                        base + " El perfil solicitado no está disponible en este dispositivo."
                    else -> base
                }
            }

            is VoiceActionResult.GameAliasSaved ->
                getContext().getString(
                    R.string.voice_result_game_alias_saved,
                    result.alias.uppercase(),
                    result.game.label
                )

            is VoiceActionResult.DeviceStatus ->
                "Estado: batería " +
                    (result.status.batteryPercent?.toString() ?: "no disponible") +
                    " por ciento, térmica " + result.status.thermalLabel + "."

            is VoiceActionResult.NetworkReport ->
                NetworkVoiceResponseText.format(result)

            is VoiceActionResult.AiAdvice ->
                AiAdviceFormatter.fullResponse(getContext(), result.advice)

            VoiceActionResult.Help ->
                "Puedes decir: abre un juego, pon X4, prioriza interpolación, FPS balanceado o dime el estado."

            is VoiceActionResult.NotAvailable ->
                "No disponible. " + result.detail

            VoiceActionResult.RequiresPermission ->
                "Se necesita permiso de micrófono."

            is VoiceActionResult.Failed ->
                "No se pudo completar. " + result.detail
        }

    private fun speak(text: String) {
        if (tts == null) {
            tts = TextToSpeech(getContext()) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.getDefault()
                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "gamehub-ultra-session")
                }
            }
        } else {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "gamehub-ultra-session")
        }
    }

    private fun speakAndFinish(text: String) {
        speak(text)
        mainHandler.postDelayed({ finish() }, 1600L)
    }

    private fun stopRecognizer() {
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
    }

    override fun onDestroy() {
        stopRecognizer()
        executor.shutdownNow()
        tts?.stop()
        tts?.shutdown()
        tts = null
        aiAdvisor.close()
        super.onDestroy()
    }
}
