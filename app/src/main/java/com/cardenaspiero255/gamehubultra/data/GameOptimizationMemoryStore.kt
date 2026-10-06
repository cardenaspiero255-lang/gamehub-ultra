package com.cardenaspiero255.gamehubultra.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cardenaspiero255.gamehubultra.domain.OptimizationFeedbackDecision
import com.cardenaspiero255.gamehubultra.domain.OptimizationObservation
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
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

    internal val lookupSerializedKeys: Set<String>
        get() = buildSet {
            add(serialized)
            legacyNoGpuSerializedKey()?.let(::add)
        }

    private fun legacyNoGpuSerializedKey(): String? {
        if (driverFingerprint != null || !deviceFingerprint.endsWith("¦")) return null
        val legacyDeviceFingerprint = deviceFingerprint + "|"
        return listOf(
            legacyDeviceFingerprint,
            gamePackage,
            gameVersion.orEmpty(),
            emulatorBackend.orEmpty(),
            "|"
        ).joinToString("¦")
    }
}

class GameOptimizationMemoryStore(
    private val dataStore: DataStore<Preferences>,
    private val maxObservations: Int = 120,
    private val maxContexts: Int = 16
) : GameOptimizationMemoryStateRepository {
    constructor(context: Context) : this(context.applicationContext.optimizationMemoryDataStore)

    private val observationsKey = stringPreferencesKey("observations_v1")

    override fun observationsFlow(contextKey: OptimizationContextKey): Flow<List<OptimizationObservation>> {
        val lookupKeys = contextKey.lookupSerializedKeys
        return dataStore.data.map { preferences ->
            preferences[observationsKey]
                .orEmpty()
                .lineSequence()
                .mapNotNull(::decode)
                .filter { it.contextKey in lookupKeys }
                .sortedByDescending { it.timestampMillis }
                .take(maxObservations)
                .toList()
        }
    }

    override suspend fun record(contextKey: OptimizationContextKey, observation: OptimizationObservation) {
        val lookupKeys = contextKey.lookupSerializedKeys
        dataStore.edit { preferences ->
            val all = preferences[observationsKey]
                .orEmpty()
                .lineSequence()
                .mapNotNull(::decode)
                .map { stored ->
                    if (stored.contextKey in lookupKeys) {
                        stored.copy(contextKey = contextKey.serialized)
                    } else {
                        stored
                    }
                }
                .filterNot { it.id == observation.id }
                .toMutableList()
            all += observation.copy(contextKey = contextKey.serialized)
            val retainedByContext = all
                .groupBy { it.contextKey }
                .values
                .map { observations ->
                    observations
                        .sortedByDescending { it.timestampMillis }
                        .take(maxObservations)
                }
                .sortedByDescending { observations ->
                    observations.maxOfOrNull(OptimizationObservation::timestampMillis) ?: Long.MIN_VALUE
                }
                .take(maxContexts)
                .flatten()
                .sortedByDescending { it.timestampMillis }

            preferences[observationsKey] = retainedByContext
                .joinToString("\n", transform = ::encode)
        }
    }

    override suspend fun pruneTo(contextKey: OptimizationContextKey) {
        val lookupKeys = contextKey.lookupSerializedKeys
        dataStore.edit { preferences ->
            val all = preferences[observationsKey]
                .orEmpty()
                .lineSequence()
                .mapNotNull(::decode)
                .filter { it.contextKey in lookupKeys }
                .map { it.copy(contextKey = contextKey.serialized) }
            preferences[observationsKey] = all.joinToString("\n", transform = ::encode)
        }
    }

    override suspend fun clearAll() {
        dataStore.edit { preferences -> preferences.remove(observationsKey) }
    }

    override suspend fun clearGame(contextKey: OptimizationContextKey) {
        val lookupKeys = contextKey.lookupSerializedKeys
        dataStore.edit { preferences ->
            val all = preferences[observationsKey]
                .orEmpty()
                .lineSequence()
                .mapNotNull(::decode)
                .filterNot { it.contextKey in lookupKeys }
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
            observation.timestampMillis.toString(),
            observation.feedbackDecision.name
        ).joinToString("|") {
            Base64.getEncoder().encodeToString(it.toByteArray(StandardCharsets.UTF_8))
        }

    private fun decode(line: String): OptimizationObservation? = runCatching {
        val fields = line.split("|")
        if (fields.size != 11 && fields.size != 12) return null
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
            timestampMillis = decodeField(fields[10]).toLongOrNull() ?: return null,
            feedbackDecision = fields.getOrNull(11)
                ?.let(::decodeField)
                ?.let { encoded ->
                    runCatching { OptimizationFeedbackDecision.valueOf(encoded) }
                        .getOrDefault(OptimizationFeedbackDecision.NONE)
                }
                ?: OptimizationFeedbackDecision.NONE
        )
    }.getOrNull()

    private fun decodeField(value: String): String =
        String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8)
}
