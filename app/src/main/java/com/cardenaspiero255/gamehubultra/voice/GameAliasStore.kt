package com.cardenaspiero255.gamehubultra.voice

import android.content.Context

object GameAliasStore {
    private const val PREFS = "gamehub_ultra_game_aliases"
    private const val KEY_ALIASES = "aliases"
    private val lock = Any()

    fun aliases(context: Context): Map<String, String> = synchronized(lock) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_ALIASES, emptySet())
            .orEmpty()
            .mapNotNull { entry ->
                val parts = entry.split('\t', limit = 2)
                if (parts.size != 2) return@mapNotNull null
                val alias = VoiceCommandParser.canonicalGameAlias(parts[0])
                val packageName = parts[1].trim()
                if (alias.isBlank() || packageName.isBlank()) null else alias to packageName
            }
            .toMap()
    }

    fun save(context: Context, alias: String, packageName: String) {
        val normalizedAlias = VoiceCommandParser.canonicalGameAlias(alias)
        if (
            normalizedAlias.length !in 2..20 ||
            VoiceCommandParser.isReservedGameAlias(normalizedAlias) ||
            packageName.isBlank()
        ) return

        synchronized(lock) {
            val updated = aliases(context).toMutableMap()
            updated[normalizedAlias] = packageName
            val serialized = updated
                .map { (key, value) -> "$key\t$value" }
                .toSet()

            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putStringSet(KEY_ALIASES, serialized)
                .apply()
        }
    }
}
