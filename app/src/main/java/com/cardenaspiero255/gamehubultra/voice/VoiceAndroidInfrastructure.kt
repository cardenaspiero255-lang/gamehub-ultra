package com.cardenaspiero255.gamehubultra.voice

import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.cardenaspiero255.gamehubultra.R
import com.cardenaspiero255.gamehubultra.ai.AiAdviceFormatter
import com.cardenaspiero255.gamehubultra.platform.BatteryTelemetry

internal object VoiceDeviceStatusProvider {
    fun read(context: Context): VoiceDeviceStatus {
        val batteryManager = context.getSystemService(android.os.BatteryManager::class.java)
        val powerManager = context.getSystemService(PowerManager::class.java)
        val battery = BatteryTelemetry.sanitizePercentage(
            batteryManager?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        )
        val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when (powerManager?.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> context.getString(R.string.normal)
                PowerManager.THERMAL_STATUS_LIGHT -> context.getString(R.string.thermal_light)
                PowerManager.THERMAL_STATUS_MODERATE -> context.getString(R.string.thermal_moderate)
                PowerManager.THERMAL_STATUS_SEVERE -> context.getString(R.string.thermal_severe)
                PowerManager.THERMAL_STATUS_CRITICAL -> context.getString(R.string.thermal_critical)
                PowerManager.THERMAL_STATUS_EMERGENCY -> context.getString(R.string.thermal_emergency)
                PowerManager.THERMAL_STATUS_SHUTDOWN -> context.getString(R.string.thermal_shutdown)
                else -> context.getString(R.string.thermal_unknown)
            }
        } else {
            context.getString(R.string.not_available)
        }
        return VoiceDeviceStatus(battery, thermal)
    }
}

internal object VoiceResponseFormatter {
    fun format(context: Context, result: VoiceActionResult): String = when (result) {
        is VoiceActionResult.ProfileSelected ->
            context.getString(
                if (result.deferred) R.string.voice_result_profile_deferred
                else R.string.voice_result_profile_applied,
                result.profile.title
            )
        is VoiceActionResult.GameOpened -> {
            val base = context.getString(R.string.voice_result_game_opened, result.game.label)
            when {
                result.profileDeferred && result.profile != null ->
                    base + " " + context.getString(
                        R.string.voice_result_profile_deferred_short,
                        result.profile.title
                    )
                result.profileUnavailable ->
                    base + " " + context.getString(R.string.voice_result_profile_unavailable)
                result.profile != null ->
                    base + " " + context.getString(
                        R.string.voice_result_profile_applied_short,
                        result.profile.title
                    )
                else -> base
            }
        }
        is VoiceActionResult.GameAliasSaved ->
            context.getString(
                R.string.voice_result_game_alias_saved,
                result.alias.uppercase(),
                result.game.label
            )
        is VoiceActionResult.DeviceStatus ->
            context.getString(
                R.string.voice_result_status,
                result.status.batteryPercent?.toString()
                    ?: context.getString(R.string.not_available),
                result.status.thermalLabel
            )
        is VoiceActionResult.NetworkReport -> NetworkVoiceResponseText.format(result)
        is VoiceActionResult.AiAdvice -> AiAdviceFormatter.fullResponse(context, result.advice)
        VoiceActionResult.Help -> context.getString(R.string.voice_result_help)
        is VoiceActionResult.NotAvailable ->
            context.getString(R.string.voice_status_unavailable) + " " + result.detail
        VoiceActionResult.RequiresPermission ->
            context.getString(R.string.voice_permission_required)
        is VoiceActionResult.Failed ->
            context.getString(R.string.voice_status_failed) + " " + result.detail
    }
}
