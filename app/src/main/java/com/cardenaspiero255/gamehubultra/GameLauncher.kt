package com.cardenaspiero255.gamehubultra

import android.content.Context
import android.content.Intent
import android.util.Log

object GameLauncher {
    private const val TAG = "GameHubUltraLauncher"

    fun launch(context: Context, packageName: String): Boolean =
        resolveAndLaunch(
            packageName = packageName,
            resolver = { context.packageManager.getLaunchIntentForPackage(it) },
            starter = context::startActivity
        )

    internal fun <T> resolveAndLaunch(
        packageName: String,
        resolver: (String) -> T?,
        starter: (T) -> Unit
    ): Boolean {
        val value = try {
            resolver(packageName)
        } catch (error: Exception) {
            Log.w(TAG, "Unable to resolve launch target for package=$packageName", error)
            return false
        }

        if (value == null) {
            return false
        }

        return try {
            starter(value)
            true
        } catch (error: Exception) {
            Log.w(TAG, "Unable to start launch target for package=$packageName", error)
            false
        }
    }
}
