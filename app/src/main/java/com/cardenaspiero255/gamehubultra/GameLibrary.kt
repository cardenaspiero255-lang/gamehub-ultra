package com.cardenaspiero255.gamehubultra

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build

data class GameInfo(
    val packageName: String,
    val label: String
)

data class GameDiscoveryResult(
    val games: List<GameInfo>,
    val failed: Boolean = false
)

object GameLibrary {
    fun discover(context: Context): GameDiscoveryResult = runCatching {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val games = context.packageManager
            .queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .asSequence()
            .filter { it.activityInfo.packageName != context.packageName }
            .filter {
                isGameApplication(
                    category = it.activityInfo.applicationInfo.category,
                    flags = it.activityInfo.applicationInfo.flags,
                    sdkInt = Build.VERSION.SDK_INT
                )
            }
            .map {
                GameInfo(
                    packageName = it.activityInfo.packageName,
                    label = it.loadLabel(context.packageManager).toString()
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
            .toList()

        GameDiscoveryResult(games)
    }.getOrElse {
        GameDiscoveryResult(emptyList(), failed = true)
    }

    internal fun isGameApplication(category: Int, flags: Int, sdkInt: Int): Boolean {
        val isDeclaredGame = category == ApplicationInfo.CATEGORY_GAME
        val isFlaggedGame = sdkInt >= Build.VERSION_CODES.O &&
            (flags and ApplicationInfo.FLAG_IS_GAME) != 0
        return isDeclaredGame || isFlaggedGame
    }
}
