package com.cardenaspiero255.gamehubultra.data

import android.content.Context
import com.cardenaspiero255.gamehubultra.domain.GamePlatform
import org.json.JSONArray
import org.json.JSONObject

class StoreLibraryStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "gamehub_ultra_store_library",
        Context.MODE_PRIVATE
    )

    fun getAll(): List<StoreLibraryGame> {
        val raw = prefs.getString("games_v1", null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList(array.length()) {
                repeat(array.length()) { index ->
                    val item = array.optJSONObject(index) ?: return@repeat
                    val platform = runCatching {
                        GamePlatform.valueOf(item.optString("platform"))
                    }.getOrNull() ?: return@repeat
                    val title = item.optString("title").trim()
                    val platformGameId = item.optString("platformGameId").trim()
                    if (title.isBlank() || platformGameId.isBlank()) return@repeat
                    add(
                        StoreLibraryGame(
                            id = item.optString("id"),
                            accountId = item.optString("accountId"),
                            platform = platform,
                            title = title,
                            platformGameId = platformGameId,
                            artworkUrl = item.optString("artworkUrl")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun replaceForAccount(accountId: String, games: List<StoreLibraryGame>) {
        val retained = getAll().filterNot { it.accountId == accountId }
        val merged = retained + games.distinctBy { "${it.platform}:${it.accountId}:${it.platformGameId}" }
        val array = JSONArray()
        merged.forEach { game ->
            array.put(
                JSONObject()
                    .put("id", game.id)
                    .put("accountId", game.accountId)
                    .put("platform", game.platform.name)
                    .put("title", game.title)
                    .put("platformGameId", game.platformGameId)
                    .put("artworkUrl", game.artworkUrl)
            )
        }
        prefs.edit().putString("games_v1", array.toString()).apply()
    }

    fun removeForAccount(accountId: String) {
        replaceForAccount(accountId, emptyList())
    }
}
