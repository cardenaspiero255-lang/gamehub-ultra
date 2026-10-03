package com.cardenaspiero255.gamehubultra.ai.persistence

import android.content.Context
import com.cardenaspiero255.gamehubultra.ai.UltraAnswerConfidence
import com.cardenaspiero255.gamehubultra.ai.UltraResearchPersistentEntry
import com.cardenaspiero255.gamehubultra.ai.UltraResearchPersistentStore
import com.cardenaspiero255.gamehubultra.ai.UltraVerifiedResearchResult
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/**
 * Bounded, privacy-aware disk cache for stable general-knowledge answers.
 *
 * Query text is never stored as a SharedPreferences key: the normalized
 * research cache key is hashed with SHA-256 before persistence.
 */
class SharedPreferencesUltraResearchPersistentStore(
    context: Context
) : UltraResearchPersistentStore {

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    override fun read(key: String): UltraResearchPersistentEntry? {
        val storageKey = storageKey(key)
        val raw = prefs.getString(storageKey, null) ?: return null
        return runCatching { decode(raw) }
            .getOrElse {
                prefs.edit().remove(storageKey).apply()
                null
            }
    }

    override fun write(
        key: String,
        entry: UltraResearchPersistentEntry
    ) {
        val storageKey = storageKey(key)
        prefs.edit()
            .putString(storageKey, encode(entry))
            .apply()
        prune()
    }

    override fun remove(key: String) {
        prefs.edit().remove(storageKey(key)).apply()
    }

    private fun encode(entry: UltraResearchPersistentEntry): String {
        val result = entry.result
        return JSONObject()
            .put("schema", SCHEMA_VERSION)
            .put("expiresAtMillis", entry.expiresAtMillis)
            .put("storedAtMillis", System.currentTimeMillis())
            .put("message", result.message)
            .put("confidence", result.confidence.name)
            .put("sources", JSONArray(result.sources))
            .put("timedOut", result.timedOut)
            .put("fallbackUsed", result.fallbackUsed)
            .toString()
    }

    private fun decode(raw: String): UltraResearchPersistentEntry {
        val json = JSONObject(raw)
        require(json.optInt("schema") == SCHEMA_VERSION)
        val message = json.getString("message").trim()
        require(message.isNotEmpty())

        val confidence = UltraAnswerConfidence.valueOf(
            json.getString("confidence")
        )
        val sourceArray = json.optJSONArray("sources") ?: JSONArray()
        val sources = buildList {
            for (index in 0 until sourceArray.length()) {
                sourceArray.optString(index)
                    .trim()
                    .takeIf(String::isNotEmpty)
                    ?.let(::add)
            }
        }

        return UltraResearchPersistentEntry(
            result = UltraVerifiedResearchResult(
                message = message,
                confidence = confidence,
                sources = sources,
                abstained = false,
                fromCache = false,
                timedOut = json.optBoolean("timedOut", false),
                fallbackUsed = json.optBoolean("fallbackUsed", false)
            ),
            expiresAtMillis = json.getLong("expiresAtMillis")
        )
    }

    private fun prune() {
        val snapshots = prefs.all
            .mapNotNull { (key, value) ->
                val raw = value as? String ?: return@mapNotNull null
                val storedAt = runCatching {
                    JSONObject(raw).optLong("storedAtMillis", 0L)
                }.getOrDefault(0L)
                key to storedAt
            }
            .sortedBy { (_, storedAt) -> storedAt }

        val overflow = snapshots.size - MAX_ENTRIES
        if (overflow <= 0) return

        val editor = prefs.edit()
        snapshots.take(overflow).forEach { (key, _) ->
            editor.remove(key)
        }
        editor.apply()
    }

    private fun storageKey(rawKey: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(rawKey.toByteArray(Charsets.UTF_8))
        return KEY_PREFIX + digest.joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }

    private companion object {
        const val PREFS_NAME = "ultra_stable_knowledge_cache"
        const val KEY_PREFIX = "v1_"
        const val SCHEMA_VERSION = 1
        const val MAX_ENTRIES = 64
    }
}
