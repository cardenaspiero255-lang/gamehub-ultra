package com.cardenaspiero255.gamehubultra

import android.content.Context
import android.content.Intent

object GameLauncher {
    fun launch(context: Context, packageName: String): Boolean {
        val intent = runCatching {
            context.packageManager.getLaunchIntentForPackage(packageName)
        }.getOrNull()
            ?: return false

        return launchIntent(context, intent)
    }

    internal fun launchIntent(context: Context, intent: Intent?): Boolean {
        if (intent == null) return false

        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
