package com.cardenaspiero255.gamehubultra.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.migrations.SharedPreferencesMigration
import androidx.datastore.preferences.core.stringPreferencesKey
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

    private fun gameProfileKey(packageName: String): Preferences.Key<String> =
        stringPreferencesKey("game_profile_$packageName")

    private fun decodeProfile(value: String?): PerformanceProfile? =
        value?.let { raw ->
            PerformanceProfile.entries.firstOrNull { it.name == raw }
        }
}

private fun Flow<Preferences>.safePreferences(): Flow<Preferences> =
    catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
