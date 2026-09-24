package com.cardenaspiero255.gamehubultra.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cardenaspiero255.gamehubultra.domain.GameProfileConfig
import com.cardenaspiero255.gamehubultra.domain.PerformanceEvent
import com.cardenaspiero255.gamehubultra.domain.PerformanceEventCodec
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.domain.ThermalPreference
import com.cardenaspiero255.gamehubultra.domain.OrientationPreference
import com.cardenaspiero255.gamehubultra.domain.ResolutionTarget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.gameHubDataStore by preferencesDataStore(
    name = "gamehub_ultra",
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, "gamehub_ultra"))
    },
    corruptionHandler = ReplaceFileCorruptionHandler {
        emptyPreferences()
    }
)

class GameHubPreferencesRepository(
    private val dataStore: DataStore<Preferences>
) {
    constructor(context: Context) : this(context.applicationContext.gameHubDataStore)

    private val selectedProfileKey = stringPreferencesKey("selected_profile")
    private val selectedGameKey = stringPreferencesKey("selected_game_package")
    private val favoriteGamesKey = stringSetPreferencesKey("favorite_games")
    private val recentGamesKey = stringPreferencesKey("recent_games")
    private val manualGamesKey = stringSetPreferencesKey("manual_game_packages")
    private val performanceHistoryKey = stringPreferencesKey("performance_history")

    fun selectedProfileFlow(): Flow<PerformanceProfile> =
        dataStore.data.map { preferences ->
            decodeProfile(preferences[selectedProfileKey]) ?: PerformanceProfile.BALANCED
        }

    fun selectedGameFlow(): Flow<String?> =
        dataStore.data.map { preferences -> preferences[selectedGameKey] }

    fun profileForGameFlow(packageName: String): Flow<PerformanceProfile?> =
        gameProfileConfigFlow(packageName).map { it?.performanceProfile }

    fun gameProfileConfigFlow(packageName: String): Flow<GameProfileConfig?> =
        dataStore.data.map { preferences ->
            val profile = decodeProfile(preferences[gameProfileKey(packageName)])
            val thermal = decodeThermalPreference(preferences[gameThermalKey(packageName)])
            val refresh = preferences[gameRefreshKey(packageName)]
                ?.toIntOrNull()
                ?.takeIf { it in 30..360 }
            val resolution = decodeResolution(preferences[gameResolutionKey(packageName)])
            val orientation = decodeOrientation(preferences[gameOrientationKey(packageName)])

            if (profile == null && thermal == null && refresh == null && resolution == null && orientation == null) {
                null
            } else {
                GameProfileConfig(
                    performanceProfile = profile ?: PerformanceProfile.BALANCED,
                    thermalPreference = thermal ?: ThermalPreference.ADAPTIVE,
                    refreshRateTargetHz = refresh,
                    resolutionTarget = resolution,
                    orientationPreference = orientation ?: OrientationPreference.AUTO
                )
            }
        }

    fun favoriteGamesFlow(): Flow<Set<String>> =
        dataStore.data.map { preferences ->
            preferences[favoriteGamesKey] ?: emptySet()
        }

    fun recentGamesFlow(): Flow<List<String>> =
        dataStore.data.map { preferences ->
            preferences[recentGamesKey]
                .orEmpty()
                .split(',')
                .map(String::trim)
                .filter(String::isNotEmpty)
        }

    fun manualGamesFlow(): Flow<Set<String>> =
        dataStore.data.map { preferences ->
            preferences[manualGamesKey] ?: emptySet()
        }

    fun performanceHistoryFlow(limit: Int = 20): Flow<List<PerformanceEvent>> =
        dataStore.data.map { preferences ->
            preferences[performanceHistoryKey]
                .orEmpty()
                .lineSequence()
                .mapNotNull(PerformanceEventCodec::decode)
                .toList()
                .takeLast(limit.coerceIn(1, 50))
        }

    suspend fun saveSelectedProfile(profile: PerformanceProfile) {
        dataStore.edit { preferences ->
            preferences[selectedProfileKey] = profile.name
        }
    }

    suspend fun saveSelectedGame(packageName: String) {
        dataStore.edit { preferences ->
            preferences[selectedGameKey] = packageName
        }
    }

    suspend fun saveSelectedGameAndProfile(
        packageName: String,
        profile: PerformanceProfile
    ) {
        dataStore.edit { preferences ->
            preferences[selectedGameKey] = packageName
            preferences[gameProfileKey(packageName)] = profile.name
        }
    }

    suspend fun saveProfileForGame(
        packageName: String,
        profile: PerformanceProfile
    ) {
        dataStore.edit { preferences ->
            preferences[gameProfileKey(packageName)] = profile.name
        }
    }

    suspend fun saveGameProfileConfig(
        packageName: String,
        config: GameProfileConfig
    ) {
        dataStore.edit { preferences ->
            preferences[gameProfileKey(packageName)] = config.performanceProfile.name
            preferences[gameThermalKey(packageName)] = config.thermalPreference.name
            config.refreshRateTargetHz
                ?.takeIf { it in 30..360 }
                ?.let { preferences[gameRefreshKey(packageName)] = it.toString() }
                ?: preferences.remove(gameRefreshKey(packageName))
            config.resolutionTarget
                ?.takeIf { it.width in 240..7680 && it.height in 240..7680 }
                ?.let { preferences[gameResolutionKey(packageName)] = "${it.width}x${it.height}" }
                ?: preferences.remove(gameResolutionKey(packageName))
            preferences[gameOrientationKey(packageName)] = config.orientationPreference.name
        }
    }

    suspend fun setFavoriteGame(packageName: String, favorite: Boolean) {
        dataStore.edit { preferences ->
            val current = preferences[favoriteGamesKey].orEmpty().toMutableSet()
            if (favorite) current += packageName else current -= packageName
            preferences[favoriteGamesKey] = current
        }
    }

    suspend fun recordRecentGame(packageName: String) {
        dataStore.edit { preferences ->
            val current = preferences[recentGamesKey]
                .orEmpty()
                .split(',')
                .map(String::trim)
                .filter(String::isNotEmpty)
                .toMutableList()
            current.remove(packageName)
            current.add(0, packageName)
            preferences[recentGamesKey] = current.take(10).joinToString(",")
        }
    }

    suspend fun setManualGame(packageName: String, manual: Boolean) {
        dataStore.edit { preferences ->
            val current = preferences[manualGamesKey].orEmpty().toMutableSet()
            if (manual) current += packageName else current -= packageName
            preferences[manualGamesKey] = current
        }
    }

    suspend fun appendPerformanceEvent(event: PerformanceEvent) {
        dataStore.edit { preferences ->
            val current = preferences[performanceHistoryKey]
                .orEmpty()
                .lineSequence()
                .filter(String::isNotBlank)
                .toMutableList()
            current += PerformanceEventCodec.encode(event)
            preferences[performanceHistoryKey] = current.takeLast(50).joinToString("\n")
        }
    }

    private fun gameProfileKey(packageName: String): Preferences.Key<String> =
        stringPreferencesKey("game_profile_$packageName")

    private fun gameThermalKey(packageName: String): Preferences.Key<String> =
        stringPreferencesKey("game_thermal_$packageName")

    private fun gameRefreshKey(packageName: String): Preferences.Key<String> =
        stringPreferencesKey("game_refresh_$packageName")

    private fun gameResolutionKey(packageName: String): Preferences.Key<String> =
        stringPreferencesKey("game_resolution_${packageName}")

    private fun gameOrientationKey(packageName: String): Preferences.Key<String> =
        stringPreferencesKey("game_orientation_${packageName}")

    private fun decodeProfile(value: String?): PerformanceProfile? =
        value?.let { raw ->
            PerformanceProfile.entries.firstOrNull { it.name == raw }
        }

    private fun decodeThermalPreference(value: String?): ThermalPreference? =
        value?.let { raw ->
            ThermalPreference.entries.firstOrNull { it.name == raw }
        }

    private fun decodeOrientation(value: String?): OrientationPreference? =
        value?.let { raw ->
            OrientationPreference.entries.firstOrNull { it.name == raw }
        }

    private fun decodeResolution(value: String?): ResolutionTarget? =
        value?.split('x', limit = 2)?.takeIf { it.size == 2 }?.let { parts ->
            val width = parts[0].toIntOrNull()
            val height = parts[1].toIntOrNull()
            if (width != null && height != null && width in 240..7680 && height in 240..7680) {
                ResolutionTarget(width, height)
            } else null
        }
}
