package com.cardenaspiero255.gamehubultra

import android.content.Context
import android.content.Intent

object GameLauncher {
    fun launch(context: Context, packageName: String): Boolean =
        launch(context, packageName) {
            context.packageManager.getLaunchIntentForPackage(it)
        }

    internal fun launch(
        context: Context,
        packageName: String,
        intentResolver: (String) -> Intent?
    ): Boolean {
        val intent = try {
            intentResolver(packageName)
        } catch (_: Exception) {
            return false
        }

        return launchIntent(context, intent)
    }

    internal fun launchIntent(context: Context, intent: Intent?): Boolean {
        if (intent == null) return false

        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
