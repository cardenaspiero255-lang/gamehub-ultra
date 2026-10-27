package com.cardenaspiero255.gamehubultra

import android.content.Context
import android.util.Log
import com.cardenaspiero255.gamehubultra.platform.gpu.AndroidGraphicsDriverPreferences
import com.cardenaspiero255.gamehubultra.platform.gpu.DriverPreferenceStatus
import com.cardenaspiero255.gamehubultra.session.SessionCoachMonitorService

object GameLauncher {
    fun launch(context: Context, packageName: String): Boolean {
        // CAR-54: inspect the current system preference immediately before game
        // launch. This read-only preflight is NEVER a privileged driver switch.
        val readback = AndroidGraphicsDriverPreferences.inspect(context, packageName)
        if (readback.status == DriverPreferenceStatus.UNVERIFIABLE ||
            readback.status == DriverPreferenceStatus.MANUAL_CONFIGURATION_REQUIRED
        ) {
            Log.i("GameLauncher", "GPU driver preference unverified; Android retains control")
        }
        return resolveAndLaunch(
            packageName = packageName,
            resolver = { context.packageManager.getLaunchIntentForPackage(it) },
            starter = context::startActivity,
            beforeStart = {
                SessionCoachMonitorService.start(context, packageName)
            },
            onStartFailure = {
                SessionCoachMonitorService.cancelLaunch(context)
            }
        )
    }

    internal fun <T> resolveAndLaunch(
        packageName: String,
        resolver: (String) -> T?,
        starter: (T) -> Unit,
        beforeStart: () -> Unit = {},
        onStartFailure: () -> Unit = {}
    ): Boolean {
        if (packageName.isBlank()) return false

        val value = try {
            resolver(packageName)
        } catch (_: Exception) {
            return false
        }

        if (value == null) {
            return false
        }

        runCatching(beforeStart)

        return try {
            starter(value)
            true
        } catch (_: Exception) {
            runCatching(onStartFailure)
            false
        }
    }
}
