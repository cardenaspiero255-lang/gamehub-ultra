package com.cardenaspiero255.gamehubultra

import android.content.Context

object GameLauncher {
    fun launch(context: Context, packageName: String): Boolean {
        if (packageName.isBlank()) return false
        return resolveAndLaunch(
            packageName = packageName,
            resolver = { context.packageManager.getLaunchIntentForPackage(it) },
            starter = context::startActivity
        )
    }

    internal fun <T> resolveAndLaunch(
        packageName: String,
        resolver: (String) -> T?,
        starter: (T) -> Unit
    ): Boolean {
        val value = try {
            resolver(packageName)
        } catch (_: Exception) {
            return false
        }

        if (value == null) {
            return false
        }

        return try {
            starter(value)
            true
        } catch (_: Exception) {
            false
        }
    }
}
