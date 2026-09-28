package com.cardenaspiero255.gamehubultra.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

internal enum class ContinuousVoiceChange {
    ENABLED,
    DISABLED,
    PERMISSION_REQUIRED
}

internal interface ContinuousVoiceGateway {
    fun isEnabled(): Boolean
    fun setEnabled(enabled: Boolean)
    fun hasRecordAudioPermission(): Boolean
    fun startWakeService()
    fun stopWakeService()
}

internal class ContinuousVoiceController(
    private val gateway: ContinuousVoiceGateway
) {
    fun isEnabled(): Boolean = gateway.isEnabled()

    fun resumeIfEnabled(): Boolean {
        if (!gateway.isEnabled() || !gateway.hasRecordAudioPermission()) {
            return false
        }
        gateway.startWakeService()
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

        gateway.setEnabled(true)
        gateway.startWakeService()
        return ContinuousVoiceChange.ENABLED
    }

    fun enableAfterPermissionGranted(): ContinuousVoiceChange {
        if (!gateway.hasRecordAudioPermission()) {
            return ContinuousVoiceChange.PERMISSION_REQUIRED
        }
        gateway.setEnabled(true)
        gateway.startWakeService()
        return ContinuousVoiceChange.ENABLED
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
