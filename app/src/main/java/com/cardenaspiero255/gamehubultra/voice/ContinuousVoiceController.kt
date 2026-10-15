package com.cardenaspiero255.gamehubultra.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.speech.SpeechRecognizer

internal enum class ContinuousVoiceChange {
    ENABLED,
    DISABLED,
    PERMISSION_REQUIRED,
    SYSTEM_RESTRICTED
}

internal interface ContinuousVoiceGateway {
    fun isEnabled(): Boolean
    fun setEnabled(enabled: Boolean)
    fun hasRecordAudioPermission(): Boolean
    fun canRunContinuousVoice(): Boolean
    fun startWakeService()
    fun stopWakeService()
}

internal class ContinuousVoiceController(
    private val gateway: ContinuousVoiceGateway
) {
    fun isEnabled(): Boolean = gateway.isEnabled()

    fun resumeIfEnabled(): Boolean {
        if (!gateway.isEnabled()) return false
        if (!gateway.hasRecordAudioPermission() || !gateway.canRunContinuousVoice()) {
            gateway.setEnabled(false)
            gateway.stopWakeService()
            return false
        }
        if (!startServiceSafely()) {
            disableAfterStartFailure()
            return false
        }
        return true
    }

    fun setEnabled(enabled: Boolean): ContinuousVoiceChange {
        if (!enabled) {
            gateway.setEnabled(false)
            gateway.stopWakeService()
            return ContinuousVoiceChange.DISABLED
        }

        if (!gateway.hasRecordAudioPermission()) {
            return ContinuousVoiceChange.PERMISSION_REQUIRED
        }
        if (!gateway.canRunContinuousVoice()) {
            gateway.setEnabled(false)
            gateway.stopWakeService()
            return ContinuousVoiceChange.SYSTEM_RESTRICTED
        }

        if (!startServiceSafely()) {
            disableAfterStartFailure()
            return ContinuousVoiceChange.SYSTEM_RESTRICTED
        }
        gateway.setEnabled(true)
        return ContinuousVoiceChange.ENABLED
    }

    fun enableAfterPermissionGranted(): ContinuousVoiceChange =
        setEnabled(true)

    private fun startServiceSafely(): Boolean =
        try {
            gateway.startWakeService()
            true
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalStateException) {
            // Android may disallow foreground microphone capture while the app
            // is backgrounded. Do not persist a mode that could not start.
            false
        }

    private fun disableAfterStartFailure() {
        gateway.setEnabled(false)
        runCatching { gateway.stopWakeService() }
    }
}

internal class AndroidContinuousVoiceGateway(
    context: Context
) : ContinuousVoiceGateway {
    private val appContext = context.applicationContext
    private val preferences =
        appContext.getSharedPreferences(VOICE_PREFS, Context.MODE_PRIVATE)

    override fun isEnabled(): Boolean =
        preferences.getBoolean(VOICE_CONTINUOUS_KEY, false)

    override fun setEnabled(enabled: Boolean) {
        preferences.edit()
            .putBoolean(VOICE_CONTINUOUS_KEY, enabled)
            .apply()
    }

    override fun canRunContinuousVoice(): Boolean =
        SpeechRecognizer.isRecognitionAvailable(appContext)

    override fun hasRecordAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    override fun startWakeService() {
        if (!hasRecordAudioPermission()) return

        val intent = Intent(appContext, UltraWakeService::class.java)
            .setAction(UltraWakeService.ACTION_START)
        ContextCompat.startForegroundService(appContext, intent)
    }

    override fun stopWakeService() {
        val intent = Intent(appContext, UltraWakeService::class.java)
            .setAction(UltraWakeService.ACTION_STOP)
        appContext.stopService(intent)
    }

    private companion object {
        const val VOICE_PREFS = "gamehub_ultra_voice"
        const val VOICE_CONTINUOUS_KEY = "continuous_enabled"
    }
}
