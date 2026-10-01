package com.cardenaspiero255.gamehubultra.ui.components

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.cardenaspiero255.gamehubultra.R
import com.cardenaspiero255.gamehubultra.domain.PerformanceEvent
import com.cardenaspiero255.gamehubultra.domain.PerformanceEventType
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile

internal fun packageVersionName(context: Context, packageName: String): String? =
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(0L)
            ).versionName
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(packageName, 0).versionName
        }
    }.getOrNull()?.takeIf(String::isNotBlank)

internal fun isPackageInstalled(context: Context, packageName: String): Boolean =
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getApplicationInfo(
                packageName,
                PackageManager.ApplicationInfoFlags.of(0L)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getApplicationInfo(packageName, 0)
        }
    }.isSuccess

@Composable
internal fun localizedProfileTitle(profile: PerformanceProfile): String =
    when (profile) {
        PerformanceProfile.BALANCED -> stringResource(R.string.profile_balanced_title)
        PerformanceProfile.FRAME_INTERPOLATION -> stringResource(R.string.profile_interpolation_title)
        PerformanceProfile.X4 -> stringResource(R.string.profile_x4_title)
    }

@Composable
internal fun localizedProfileDescription(profile: PerformanceProfile): String =
    when (profile) {
        PerformanceProfile.BALANCED -> stringResource(R.string.profile_balanced_description)
        PerformanceProfile.FRAME_INTERPOLATION -> stringResource(R.string.profile_interpolation_description)
        PerformanceProfile.X4 -> stringResource(R.string.profile_x4_description)
    }

@Composable
internal fun thermalLabel(status: Int?): String =
    when (status) {
        PowerManager.THERMAL_STATUS_NONE -> stringResource(R.string.normal)
        PowerManager.THERMAL_STATUS_LIGHT -> stringResource(R.string.thermal_light)
        PowerManager.THERMAL_STATUS_MODERATE -> stringResource(R.string.thermal_moderate)
        PowerManager.THERMAL_STATUS_SEVERE -> stringResource(R.string.thermal_severe)
        PowerManager.THERMAL_STATUS_CRITICAL -> stringResource(R.string.thermal_critical)
        PowerManager.THERMAL_STATUS_EMERGENCY -> stringResource(R.string.thermal_emergency)
        PowerManager.THERMAL_STATUS_SHUTDOWN -> stringResource(R.string.thermal_shutdown)
        null -> stringResource(R.string.not_available)
        else -> stringResource(R.string.thermal_unknown)
    }

@Composable
internal fun eventLabel(event: PerformanceEvent): String =
    when (event.type) {
        PerformanceEventType.SESSION_STARTED -> "• " + stringResource(R.string.event_session_started)
        PerformanceEventType.SESSION_ENDED -> "• " + stringResource(R.string.event_session_ended)
        PerformanceEventType.THERMAL_CHANGED ->
            "• " + stringResource(R.string.event_thermal_changed) +
                (event.detail.takeIf(String::isNotBlank)?.let { ": $it" } ?: "")
        PerformanceEventType.POLICY_CHANGED ->
            "• " + stringResource(R.string.event_policy_changed) +
                (event.profile?.title?.let { ": $it" } ?: "") +
                (event.score?.let { " ($it/100)" } ?: "")
    }
