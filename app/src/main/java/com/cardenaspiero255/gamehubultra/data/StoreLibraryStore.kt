package com.cardenaspiero255.gamehubultra.data

import android.content.Context
import com.cardenaspiero255.gamehubultra.domain.GamePlatform
import org.json.JSONArray
import org.json.JSONObject

class StoreLibraryStore(context: Context) : StoreLibraryStateRepository {
    private val prefs = context.applicationContext.getSharedPreferences(
        "gamehub_ultra_store_library",
        Context.MODE_PRIVATE
    )

    override fun getAll(): List<StoreLibraryGame> {
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
                    if (title.isBlank() || platformGameId.isBlank() ||
                        item.optString("accountId").isBlank()) return@repeat
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

    override fun replaceForAccount(accountId: String, games: List<StoreLibraryGame>) {
        val retained = retainStoreLibraryOutsideAccount(accountId, getAll())
        val replacement = normalizeStoreLibraryReplacement(accountId, games)
        val merged = retained + replacement.distinctBy {
            "${it.platform}:${it.accountId}:${it.platformGameId}"
        }
        persistGames(merged, durable = false)
    }

    override fun removeForAccount(accountId: String) {
        val retained = retainStoreLibraryOutsideAccount(accountId, getAll())
        // A privacy deletion must reach disk before the account mapping is removed.
        persistGames(retained, durable = true)
    }

    private fun persistGames(games: List<StoreLibraryGame>, durable: Boolean) {
        val array = JSONArray()
        games.forEach { game ->
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
        val editor = prefs.edit().putString("games_v1", array.toString())
        if (durable) {
            check(editor.commit()) { "Unable to durably delete cached account data" }
        } else {
            editor.apply()
        }
    }
}


private fun normalizeStoreLibraryAccountId(accountId: String): String =
    accountId.trim().also {
        require(it.isNotEmpty()) { "accountId must not be blank" }
    }

internal fun retainStoreLibraryOutsideAccount(
    accountId: String,
    games: List<StoreLibraryGame>
): List<StoreLibraryGame> {
    val normalizedAccountId = normalizeStoreLibraryAccountId(accountId)
    return games.filterNot { it.accountId == normalizedAccountId }
}

internal fun normalizeStoreLibraryReplacement(
    accountId: String,
    games: List<StoreLibraryGame>
): List<StoreLibraryGame> {
    val normalizedAccountId = normalizeStoreLibraryAccountId(accountId)
    return games.map { game ->
        if (game.accountId == normalizedAccountId) game
        else game.copy(accountId = normalizedAccountId)
    }
}
