package com.cardenaspiero255.gamehubultra.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.gameHubDataStore by preferencesDataStore(
    name = "gamehub_ultra",
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, "gamehub_ultra"))
    }
  )

class GameHubPreferencesRepository(context: Context) {
    private val appContext = context.applicationContext

    private val selectedProfileKey = stringPreferencesKey("selected_profile")
    private val selectedGameKey = stringPreferencesKey("selected_game_package")
    private val favoriteGamesKey = stringSetPreferencesKey("favorite_games")
    private val recentGamesKey = stringPreferencesKey("recent_games")

    fun selectedProfileFlow(): Flow<PerformanceProfile> =
        appContext.gameHubDataStore.data
            .safePreferences()
            .map { preferences ->
                decodeProfile(preferences[selectedProfileKey]) ?: PerformanceProfile.BALANCED
            }

    fun selectedGameFlow(): Flow<String?> =
        appContext.gameHubDataStore.data
            .safePreferences()
            .map { preferences -> preferences[selectedGameKey] }

    fun profileForGameFlow(packageName: String): Flow<PerformanceProfile?> =
        appContext.gameHubDataStore.data
            .safePreferences()
            .map { preferences ->
                decodeProfile(preferences[gameProfileKey(packageName)])
            }

    fun favoriteGamesFlow(): Flow<Set<String>> =
        appContext.gameHubDataStore.data
            .safePreferences()
            .map { preferences -> preferences[favoriteGamesKey] ?: emptySet() }

    fun recentGamesFlow(): Flow<List<String>> =
        appContext.gameHubDataStore.data
            .safePreferences()
            .map { preferences ->
                preferences[recentGamesKey]
                    .orEmpty()
                    .split(',')
                    .map(String::trim)
                    .filter(String::isNotEmpty)
            }

    suspend fun saveSelectedProfile(profile: PerformanceProfile) {
        appContext.gameHubDataStore.edit { preferences ->
            preferences[selectedProfileKey] = profile.name
        }
    }

    suspend fun saveSelectedGame(packageName: String) {
        appContext.gameHubDataStore.edit { preferences ->
            preferences[selectedGameKey] = packageName
        }
    }

    suspend fun saveProfileForGame(packageName: String, profile: PerformanceProfile) {
        appContext.gameHubDataStore.edit { preferences ->
            preferences[gameProfileKey(packageName)] = profile.name
        }
    }

    suspend fun setFavoriteGame(packageName: String, favorite: Boolean) {
        appContext.gameHubDataStore.edit { preferences ->
            val current = preferences[favoriteGamesKey].orEmpty().toMutableSet()
            if (favorite) {
                current += packageName
            } else {
                current -= packageName
            }
            preferences[favoriteGamesKey] = current
        }
    }

    suspend fun recordRecentGame(packageName: String) {
        appContext.gameHubDataStore.edit { preferences ->
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

    private fun gameProfileKey(packageName: String): Preferences.Key<String> =
        stringPreferencesKey("game_profile_$packageName")

    private fun decodeProfile(value: String?): PerformanceProfile? =
        value?.let { raw ->
            PerformanceProfile.entries.firstOrNull { it.name == raw }
        }
}

private fun Flow<Preferences>.safePreferences(): Flow<Preferences> =
    catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
