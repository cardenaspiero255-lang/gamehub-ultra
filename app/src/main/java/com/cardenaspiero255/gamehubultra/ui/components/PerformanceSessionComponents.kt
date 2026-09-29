package com.cardenaspiero255.gamehubultra.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cardenaspiero255.gamehubultra.R
import com.cardenaspiero255.gamehubultra.localizedProfileDescription
import com.cardenaspiero255.gamehubultra.localizedProfileTitle
import com.cardenaspiero255.gamehubultra.data.GameSessionRecord
import com.cardenaspiero255.gamehubultra.domain.OptimizationObservation
import com.cardenaspiero255.gamehubultra.domain.PerformanceState

internal fun formatDuration(durationMillis: Long): String {
    val totalSeconds = durationMillis / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return when {
        hours > 0L -> "${hours}h ${minutes}m"
        minutes > 0L -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}

@Composable
internal fun SessionCenterCard(
    context: Context,
    sessions: List<GameSessionRecord>,
    onClear: () -> Unit,
    onShare: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.session_center_title), style = MaterialTheme.typography.titleLarge)
                Text(sessions.size.toString(), color = MaterialTheme.colorScheme.primary)
            }
            if (sessions.isEmpty()) {
                Text(stringResource(R.string.session_center_empty), style = MaterialTheme.typography.bodySmall)
            } else {
                sessions.take(5).forEach { session ->
                    val duration = session.durationMillis?.let(::formatDuration)
                        ?: stringResource(R.string.session_active)
                    DeviceRow(
                        label = session.packageName,
                        value = session.profileName + " · " + duration
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = onShare, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.session_share))
                    }
                    TextButton(onClick = onClear, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.session_clear))
                    }
                }
            }
        }
    }
}

private object VoiceDeviceStatusProvider {
    fun read(context: Context): VoiceDeviceStatus {
        val batteryManager = context.getSystemService(android.os.BatteryManager::class.java)
        val powerManager = context.getSystemService(PowerManager::class.java)
        val battery = BatteryTelemetry.sanitizePercentage(
            batteryManager?.getIntProperty(
                android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY
            )        )
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

private object VoiceResponseFormatter {
    fun format(context: Context, result: VoiceActionResult): String =
        when (result) {
            is VoiceActionResult.ProfileSelected ->
                context.getString(
                    if (result.deferred) {
                        R.string.voice_result_profile_deferred
                    } else {
                        R.string.voice_result_profile_applied
                    },
                    result.profile.title
                )
            is VoiceActionResult.GameOpened -> {
                val base = context.getString(
                    R.string.voice_result_game_opened,
                    result.game.label
                )
                when {
                    result.profileDeferred && result.profile != null ->
                        base + " " + context.getString(
                            R.string.voice_result_profile_deferred_short,
                            result.profile.title
                        )
                    result.profileUnavailable ->
                        base + " " + context.getString(
                            R.string.voice_result_profile_unavailable
                        )
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
            is VoiceActionResult.NetworkReport ->
                NetworkVoiceResponseText.format(result)
            is VoiceActionResult.AiAdvice ->
                AiAdviceFormatter.fullResponse(context, result.advice)
            VoiceActionResult.Help ->
                context.getString(R.string.voice_result_help)
            is VoiceActionResult.NotAvailable ->
                context.getString(R.string.voice_status_unavailable) +
                    " " + result.detail
            VoiceActionResult.RequiresPermission ->
                context.getString(R.string.voice_permission_required)
            is VoiceActionResult.Failed ->
                context.getString(R.string.voice_status_failed) +
                    " " + result.detail
        }
}

@Composable
internal fun ActiveProfileCard(state: PerformanceState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(R.string.active_profile),
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                localizedProfileTitle(state.selectedProfile),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(localizedProfileDescription(state.selectedProfile))
            Text(
                if (state.sustainedModeApplied) {
                    stringResource(R.string.sustained_applied)
                } else {
                    stringResource(R.string.sustained_not_applied)
                }
            )
            Text(
                stringResource(R.string.interpolation_intent) + ": " +
                    if (state.selectedProfile.frameInterpolationIntent) {
                        stringResource(R.string.interpolation_prioritized)
                    } else {
                        stringResource(R.string.interpolation_not_requested)
                    }
            )
            Text(
                stringResource(R.string.thermal_tradeoff) + ": " +
                    if (state.selectedProfile.acceptsHigherTemperature) {
                        stringResource(R.string.accepts_higher_temperature)
                    } else {
                        stringResource(R.string.no_extra_thermal)
                    }
            )
        }
    }
}

@Composable
internal fun SmartPerformanceCard(
    recommendation: com.cardenaspiero255.gamehubultra.domain.SmartPerformanceRecommendation,
    observations: List<OptimizationObservation>,
    onApply: () -> Unit,
    onClearMemory: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(R.string.smart_performance_title),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                localizedProfileTitle(recommendation.profile) +
                    " · " + recommendation.score + "/100"
            )
            Text(recommendation.reason)
            if (recommendation.evidence.isNotEmpty()) {
                Text(
                    stringResource(R.string.smart_performance_evidence),
                    style = MaterialTheme.typography.labelLarge
                )
                recommendation.evidence.take(5).forEach { evidence ->
                    Text("• " + evidence, style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(
                stringResource(
                    R.string.smart_performance_driver,
                    recommendation.gpuFamily.name,
                    when (recommendation.driverStrategy) {
                        com.cardenaspiero255.gamehubultra.domain.DriverStrategy.SYSTEM_ONLY ->
                            stringResource(R.string.driver_system_only)
                        com.cardenaspiero255.gamehubultra.domain.DriverStrategy.TURNIP_CANDIDATE ->
                            stringResource(R.string.driver_turnip_candidate)
                        com.cardenaspiero255.gamehubultra.domain.DriverStrategy.NATIVE_OR_VENDOR_CANDIDATE ->
                            stringResource(R.string.driver_native_candidate)
                    }
                ),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                stringResource(R.string.smart_performance_observation_count, observations.size),
                style = MaterialTheme.typography.bodySmall
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onApply, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.smart_performance_apply))
                }
                TextButton(onClick = onClearMemory, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.smart_performance_reset))
                }
            }
        }
    }
}

