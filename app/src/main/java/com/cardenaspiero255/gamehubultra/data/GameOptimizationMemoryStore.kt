package com.cardenaspiero255.gamehubultra.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cardenaspiero255.gamehubultra.domain.OptimizationObservation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.nio.charset.StandardCharsets
import java.util.Base64

private val Context.optimizationMemoryDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "gamehub_ultra_optimization_memory",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

data class OptimizationContextKey(
    val deviceFingerprint: String,
    val gamePackage: String,
    val gameVersion: String?,
    val emulatorBackend: String?,
    val driverFingerprint: String?
) {
    val serialized: String
        get() = listOf(
            deviceFingerprint,
            gamePackage,
            gameVersion.orEmpty(),
            emulatorBackend.orEmpty(),
            driverFingerprint.orEmpty()
        ).joinToString("¦")
}

class GameOptimizationMemoryStore(
    private val dataStore: DataStore<Preferences>,
    private val maxObservations: Int = 120
) {
    constructor(context: Context) : this(context.applicationContext.optimizationMemoryDataStore)

    private val observationsKey = stringPreferencesKey("observations_v1")

    fun observationsFlow(contextKey: OptimizationContextKey): Flow<List<OptimizationObservation>> =
        dataStore.data.map { preferences ->
            preferences[observationsKey]
                .orEmpty()
                .lineSequence()
                .mapNotNull(::decode)
                .filter { it.contextKey == contextKey.serialized }
                .sortedByDescending { it.timestampMillis }
                .take(maxObservations)
                .toList()
        }

    suspend fun record(contextKey: OptimizationContextKey, observation: OptimizationObservation) {
        dataStore.edit { preferences ->
            val all = preferences[observationsKey]
                .orEmpty()
                .lineSequence()
                .mapNotNull(::decode)
                .filterNot { it.id == observation.id }
                .toMutableList()
            all += observation.copy(contextKey = contextKey.serialized)
            preferences[observationsKey] = all
                .sortedByDescending { it.timestampMillis }
                .take(maxObservations)
                .joinToString("\n", transform = ::encode)
        }
    }

    suspend fun pruneTo(contextKey: OptimizationContextKey) {
        dataStore.edit { preferences ->
            val all = preferences[observationsKey]
                .orEmpty()
                .lineSequence()
                .mapNotNull(::decode)
                .filter { it.contextKey == contextKey.serialized }
            preferences[observationsKey] = all.joinToString("\n", transform = ::encode)
        }
    }

    suspend fun clearAll() {
        dataStore.edit { preferences -> preferences.remove(observationsKey) }
    }

    suspend fun clearGame(contextKey: OptimizationContextKey) {
        dataStore.edit { preferences ->
            val all = preferences[observationsKey]
                .orEmpty()
                .lineSequence()
                .mapNotNull(::decode)
                .filterNot { it.contextKey == contextKey.serialized }
            preferences[observationsKey] = all.joinToString("\n", transform = ::encode)
        }
    }

    private fun encode(observation: OptimizationObservation): String =
        listOf(
            observation.id,
            observation.contextKey,
            observation.profile.name,
            observation.measuredFps?.toString().orEmpty(),
            observation.stable.toString(),
            observation.failed.toString(),
            observation.highTemperature.toString(),
            observation.thermalStatus?.toString().orEmpty(),
            observation.batteryPercent?.toString().orEmpty(),
            observation.errorReason.orEmpty(),
            observation.timestampMillis.toString()
        ).joinToString("|") {
            Base64.getEncoder().encodeToString(it.toByteArray(StandardCharsets.UTF_8))
        }

    private fun decode(line: String): OptimizationObservation? = runCatching {
        val fields = line.split("|")
        if (fields.size != 11) return null
        OptimizationObservation(
            id = decodeField(fields[0]),
            contextKey = decodeField(fields[1]),
            profile = PerformanceProfile.valueOf(decodeField(fields[2])),
            measuredFps = decodeField(fields[3]).toFloatOrNull(),
            stable = decodeField(fields[4]).toBooleanStrictOrNull() ?: false,
            failed = decodeField(fields[5]).toBooleanStrictOrNull() ?: false,
            highTemperature = decodeField(fields[6]).toBooleanStrictOrNull() ?: false,
            thermalStatus = decodeField(fields[7]).toIntOrNull(),
            batteryPercent = decodeField(fields[8]).toIntOrNull()?.takeIf { it in 0..100 },
            errorReason = decodeField(fields[9]).takeIf(String::isNotBlank),
            timestampMillis = decodeField(fields[10]).toLongOrNull() ?: return null
        )
    }.getOrNull()

    private fun decodeField(value: String): String =
        String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8)
}
