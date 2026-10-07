package com.cardenaspiero255.gamehubultra

import android.content.Context
import com.cardenaspiero255.gamehubultra.session.SessionCoachMonitorService

object GameLauncher {
    fun launch(context: Context, packageName: String): Boolean =
        resolveAndLaunch(
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
