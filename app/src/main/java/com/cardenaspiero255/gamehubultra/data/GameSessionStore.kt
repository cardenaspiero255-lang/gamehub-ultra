package com.cardenaspiero255.gamehubultra.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.nio.charset.StandardCharsets
import java.util.Base64

data class GameSessionRecord(
    val id: String,
    val packageName: String,
    val profileName: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long? = null,
    val startBatteryPercent: Int? = null,
    val endBatteryPercent: Int? = null,
    val endThermalStatus: Int? = null,
    val endRamUsedPercent: Int? = null
) {
    val durationMillis: Long?
        get() = endedAtMillis?.let { end -> (end - startedAtMillis).coerceAtLeast(0L) }
    val isActive: Boolean
        get() = endedAtMillis == null
}

private val Context.gameSessionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "gamehub_ultra_sessions",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

class GameSessionStore(
    private val dataStore: DataStore<Preferences>,
    private val maxSessions: Int = 30
) {
    constructor(context: Context) : this(context.applicationContext.gameSessionDataStore)

    fun sessionsFlow(): Flow<List<GameSessionRecord>> =
        dataStore.data.map { preferences ->
            preferences[sessionsKey]
                .orEmpty()
                .lineSequence()
                .mapNotNull(::decode)
                .sortedByDescending { it.startedAtMillis }
                .take(maxSessions.coerceIn(1, 100))
                .toList()
        }

    suspend fun startSession(record: GameSessionRecord) {
        dataStore.edit { preferences ->
            val current = preferences[sessionsKey]
                .orEmpty()
                .lineSequence()
                .mapNotNull(::decode)
                .filterNot { it.id == record.id }
                .toMutableList()
            current += record.copy(
                endedAtMillis = null,
                startBatteryPercent = sanitizePercent(record.startBatteryPercent)
            )
            preferences[sessionsKey] = current
                .sortedByDescending { it.startedAtMillis }
                .take(maxSessions.coerceIn(1, 100))
                .joinToString("\n", transform = ::encode)
        }
    }

    suspend fun finishSession(
        sessionId: String,
        endedAtMillis: Long,
        endBatteryPercent: Int?,
        endThermalStatus: Int?,
        endRamUsedPercent: Int?
    ): Boolean {
        var updated = false
        dataStore.edit { preferences ->
            val current = preferences[sessionsKey]
                .orEmpty()
                .lineSequence()
                .mapNotNull(::decode)
                .map {
                    if (it.id == sessionId && it.endedAtMillis == null) {
                        updated = true
                        it.copy(
                            endedAtMillis = maxOf(endedAtMillis, it.startedAtMillis),
                            endBatteryPercent = sanitizePercent(endBatteryPercent),
                            endThermalStatus = endThermalStatus,
                            endRamUsedPercent = sanitizePercent(endRamUsedPercent)
                        )
                    } else {
                        it
                    }
                }
                .sortedByDescending { it.startedAtMillis }
                .take(maxSessions.coerceIn(1, 100))
            preferences[sessionsKey] = current.joinToString("\n", transform = ::encode)
        }
        return updated
    }

    suspend fun finishActiveSessions(endedAtMillis: Long): Int {
        var updatedCount = 0
        dataStore.edit { preferences ->
            val current = preferences[sessionsKey]
                .orEmpty()
                .lineSequence()
                .mapNotNull(::decode)
                .map { session ->
                    if (session.endedAtMillis == null) {
                        updatedCount += 1
                        session.copy(
                            endedAtMillis = maxOf(endedAtMillis, session.startedAtMillis)
                        )
                    } else {
                        session
                    }
                }
                .sortedByDescending { it.startedAtMillis }
                .take(maxSessions.coerceIn(1, 100))
            preferences[sessionsKey] = current.joinToString("\n", transform = ::encode)
        }
        return updatedCount
    }

    suspend fun clearSessions() {
        dataStore.edit { preferences -> preferences.remove(sessionsKey) }
    }

    private fun sanitizePercent(value: Int?): Int? = value?.takeIf { it in 0..100 }

    private fun encode(record: GameSessionRecord): String =
        listOf(
            record.id,
            record.packageName,
            record.profileName,
            record.startedAtMillis.toString(),
            record.endedAtMillis?.toString().orEmpty(),
            record.startBatteryPercent?.toString().orEmpty(),
            record.endBatteryPercent?.toString().orEmpty(),
            record.endThermalStatus?.toString().orEmpty(),
            record.endRamUsedPercent?.toString().orEmpty()
        ).joinToString("|") {
            Base64.getEncoder().encodeToString(it.toByteArray(StandardCharsets.UTF_8))
        }

    private fun decode(line: String): GameSessionRecord? = runCatching {
        val fields = line.split("|")
        if (fields.size != 9) return null
        fun field(index: Int): String =
            String(Base64.getDecoder().decode(fields[index]), StandardCharsets.UTF_8)

        val started = field(3).toLongOrNull() ?: return null
        val ended = field(4).takeIf(String::isNotBlank)?.toLongOrNull()
        val startBattery = field(5).toIntOrNull()?.takeIf { it in 0..100 }
        val endBattery = field(6).toIntOrNull()?.takeIf { it in 0..100 }
        val thermal = field(7).toIntOrNull()
        val ram = field(8).toIntOrNull()?.takeIf { it in 0..100 }

        GameSessionRecord(
            id = field(0),
            packageName = field(1),
            profileName = field(2),
            startedAtMillis = started,
            endedAtMillis = ended,
            startBatteryPercent = startBattery,
            endBatteryPercent = endBattery,
            endThermalStatus = thermal,
            endRamUsedPercent = ram
        )
    }.getOrNull()

    private companion object {
        val sessionsKey = stringPreferencesKey("sessions_v1")
    }
}
