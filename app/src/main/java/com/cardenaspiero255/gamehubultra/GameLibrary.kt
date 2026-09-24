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
    internal fun filterGames(games: List<GameInfo>, query: String): List<GameInfo> {
        val normalizedQuery = query.trim().lowercase()
        if (normalizedQuery.isBlank()) return games
        return games.filter { game ->
            game.label.lowercase().contains(normalizedQuery) ||
                game.packageName.lowercase().contains(normalizedQuery)
        }
    }

    fun discover(
        context: Context,
        additionalPackages: Set<String> = emptySet()
    ): GameDiscoveryResult = runCatching {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val launchableApps = queryLaunchableApps(context, intent)
        val games = launchableApps
            .filter { app ->
                isGameApplication(
                    category = app.applicationInfo.category,
                    flags = app.applicationInfo.flags,
                    sdkInt = Build.VERSION.SDK_INT
                ) || additionalPackages.contains(app.packageName)
            }
            .map { app ->
                GameInfo(
                    packageName = app.packageName,
                    label = app.label
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }

        GameDiscoveryResult(games)
    }.getOrElse {
        GameDiscoveryResult(emptyList(), failed = true)
    }

    /** Voice commands use every launchable app because some games do not advertise CATEGORY_GAME. */
    fun discoverForVoice(context: Context): List<GameInfo> = runCatching {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        queryLaunchableApps(context, intent)
            .map { app -> GameInfo(app.packageName, app.label) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }.getOrDefault(emptyList())

    fun discoverNonGameLaunchableApps(context: Context): List<GameInfo> = runCatching {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        queryLaunchableApps(context, intent)
            .filter { app ->
                !isGameApplication(
                    category = app.applicationInfo.category,
                    flags = app.applicationInfo.flags,
                    sdkInt = Build.VERSION.SDK_INT
                )
            }
            .map { app -> GameInfo(app.packageName, app.label) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }.getOrDefault(emptyList())

    private fun queryLaunchableApps(
        context: Context,
        intent: Intent
    ): List<LaunchableApp> =
        context.packageManager
            .queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .asSequence()
            .filter { it.activityInfo.packageName != context.packageName }
            .map {
                LaunchableApp(
                    packageName = it.activityInfo.packageName,
                    applicationInfo = it.activityInfo.applicationInfo,
                    label = it.loadLabel(context.packageManager).toString()
                )
            }
            .distinctBy { it.packageName }
            .toList()

    internal fun isGameApplication(category: Int, flags: Int, sdkInt: Int): Boolean {
        val isDeclaredGame = category == ApplicationInfo.CATEGORY_GAME
        val isFlaggedGame = sdkInt >= Build.VERSION_CODES.O &&
            (flags and ApplicationInfo.FLAG_IS_GAME) != 0
        return isDeclaredGame || isFlaggedGame
    }

    private data class LaunchableApp(
        val packageName: String,
        val applicationInfo: ApplicationInfo,
        val label: String
    )
}
