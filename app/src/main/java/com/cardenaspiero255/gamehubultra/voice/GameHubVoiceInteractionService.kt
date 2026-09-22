package com.cardenaspiero255.gamehubultra.voice

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import com.cardenaspiero255.gamehubultra.GameLauncher
import com.cardenaspiero255.gamehubultra.GameLibrary
import com.cardenaspiero255.gamehubultra.GameSelectionStore
import com.cardenaspiero255.gamehubultra.ProfileSelectionStore
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import java.util.concurrent.Executors

class GameHubVoiceInteractionService : VoiceInteractionService()

class GameHubVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle): VoiceInteractionSession =
        GameHubVoiceInteractionSession(this)
}

private class GameHubVoiceInteractionSession(context: Context) :
    VoiceInteractionSession(context) {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onGetSupportedCommands(commands: Array<String>): BooleanArray =
        commands.map { it == COMMAND_EXECUTE_TEXT }.toBooleanArray()

    override fun onRequestCommand(request: CommandRequest) {
        if (request.command != COMMAND_EXECUTE_TEXT) {
            request.sendResult(Bundle().apply { putString(KEY_STATUS, "unsupported") })
            return
        }

        val transcript = request.extras?.getString(EXTRA_TRANSCRIPT).orEmpty()
        executor.execute {
            val result = VoiceCommandEngine.execute(
                command = VoiceCommandParser.parse(transcript),
                gamesProvider = { GameLibrary.discover(getContext()).games },
                launchGame = { packageName -> GameLauncher.launch(getContext(), packageName) },
                saveSelectedGame = { packageName ->
                    GameSelectionStore.saveSelectedGame(getContext(), packageName)
                },
                saveSelectedProfile = { profile ->
                    ProfileSelectionStore.saveSelectedProfile(getContext(), profile)
                },
                isProfileAvailable = { profile ->
                    profile != PerformanceProfile.X4 ||
                        com.cardenaspiero255.gamehubultra.platform.DeviceCapabilitiesProvider
                            .get(getContext()).sustainedPerformanceSupported
                },
                statusProvider = { readStatus() }
            )

            val response = responseText(result)
            mainHandler.post {
                request.sendResult(Bundle().apply {
                    putString(KEY_STATUS, "ok")
                    putString(KEY_RESPONSE, response)
                })
            }
        }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun readStatus(): VoiceDeviceStatus {
        val batteryManager = getContext().getSystemService(BatteryManager::class.java)
        val powerManager = getContext().getSystemService(PowerManager::class.java)
        val battery = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.takeIf { it in 0..100 }

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
        } else "No disponible"

        return VoiceDeviceStatus(battery, thermal)
    }

    private fun responseText(result: VoiceActionResult): String =
        when (result) {
            is VoiceActionResult.ProfileApplied ->
                "Aplicado: perfil " + result.profile.title + " seleccionado."
            is VoiceActionResult.GameOpened -> {
                val base = "Aplicado: abrí " + result.game.label + "."
                when {
                    result.profile != null ->
                        base + " Perfil " + result.profile.title + " seleccionado en GameHub Ultra."
                    result.profileUnavailable ->
                        base + " El perfil solicitado no está disponible en este dispositivo."
                    else -> base
                }
            }
            is VoiceActionResult.DeviceStatus ->
                "Estado: batería " + (result.status.batteryPercent?.toString() ?: "no disponible") +
                    " por ciento, térmica " + result.status.thermalLabel + "."
            VoiceActionResult.Help ->
                "Puedes decir: abre un juego, pon X4, prioriza interpolación, FPS balanceado o dime el estado."
            is VoiceActionResult.NotAvailable ->
                "No disponible. " + result.detail
            VoiceActionResult.RequiresPermission ->
                "Se necesita permiso de micrófono."
            is VoiceActionResult.Failed ->
                "No se pudo completar. " + result.detail
        }

    companion object {
        const val COMMAND_EXECUTE_TEXT = "com.cardenaspiero255.gamehubultra.EXECUTE_VOICE_COMMAND"
        const val EXTRA_TRANSCRIPT = "transcript"
        const val KEY_STATUS = "status"
        const val KEY_RESPONSE = "response"
    }
}
