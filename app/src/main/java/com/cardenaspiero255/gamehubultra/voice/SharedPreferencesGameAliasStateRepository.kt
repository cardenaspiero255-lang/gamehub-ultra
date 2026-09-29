package com.cardenaspiero255.gamehubultra.voice

import android.content.Context
import com.cardenaspiero255.gamehubultra.data.GameAliasStateRepository

/**
 * Android storage adapter for user-defined game aliases.
 *
 * Keeps SharedPreferences ownership behind the Android-free GameAliasStateRepository boundary.
 */
class SharedPreferencesGameAliasStateRepository(
    context: Context,
) : GameAliasStateRepository {
    private val appContext = context.applicationContext

    override fun aliases(): Map<String, String> =
        GameAliasStore.aliases(appContext)

    override fun save(alias: String, packageName: String) {
        GameAliasStore.save(appContext, alias, packageName)
    }
}
