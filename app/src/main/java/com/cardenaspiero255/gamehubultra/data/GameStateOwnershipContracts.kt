package com.cardenaspiero255.gamehubultra.data

import com.cardenaspiero255.gamehubultra.domain.GameProfileConfig
import com.cardenaspiero255.gamehubultra.domain.OptimizationObservation
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Owns persisted selection and per-game performance-profile state.
 *
 * Callers should depend on this boundary instead of knowing which Android storage mechanism
 * currently backs the state.
 */
interface GameSelectionStateRepository {
    fun selectedProfileFlow(): Flow<PerformanceProfile>
    fun selectedGameFlow(): Flow<String?>
    fun profileForGameFlow(packageName: String): Flow<PerformanceProfile?>
    fun gameProfileConfigFlow(packageName: String): Flow<GameProfileConfig?>

    suspend fun saveSelectedProfile(profile: PerformanceProfile)
    suspend fun saveSelectedGame(packageName: String)
    suspend fun saveSelectedGameAndProfile(packageName: String, profile: PerformanceProfile)
    suspend fun saveProfileForGame(packageName: String, profile: PerformanceProfile)
    suspend fun saveGameProfileConfig(packageName: String, config: GameProfileConfig)
}

/** Owns persisted Library collections independently from selection/profile state. */
suspend fun GameSelectionStateRepository.effectiveProfileForSelection(
    selectedGamePackage: String?,
): PerformanceProfile {
    val gameProfile = selectedGamePackage
        ?.let { packageName -> profileForGameFlow(packageName).first() }
    return gameProfile ?: selectedProfileFlow().first()
}

interface GameLibraryStateRepository {
    fun favoriteGamesFlow(): Flow<Set<String>>
    fun recentGamesFlow(): Flow<List<String>>
    fun manualGamesFlow(): Flow<Set<String>>

    suspend fun setFavoriteGame(packageName: String, favorite: Boolean)
    suspend fun recordRecentGame(packageName: String)
    suspend fun setManualGame(packageName: String, manual: Boolean)
}

/**
 * Android-free boundary for user-defined game aliases.
 *
 * The current SharedPreferences-backed alias store will be adapted behind this boundary in a
 * later Block 8 cut.
 */
interface GameAliasStateRepository {
    fun aliases(): Map<String, String>
    fun save(alias: String, packageName: String)
}


/** Owns persisted game-session history independently from UI lifecycle state. */
interface GameSessionStateRepository {
    fun sessionsFlow(): Flow<List<GameSessionRecord>>

    suspend fun startSession(record: GameSessionRecord)
    suspend fun finishSession(
        sessionId: String,
        endedAtMillis: Long,
        endBatteryPercent: Int?,
        endThermalStatus: Int?,
        endRamUsedPercent: Int?,
    ): Boolean
    suspend fun finishActiveSessions(endedAtMillis: Long): Int
    suspend fun clearSessions()
}


/** Owns persisted connected game accounts independently from Android UI consumers. */
interface ConnectedGameAccountsStateRepository {
    fun accountsFlow(): Flow<List<ConnectedGameAccount>>
    fun activeAccountIdFlow(): Flow<String?>
    suspend fun setActiveAccount(accountId: String?): Boolean
    suspend fun upsert(
        platform: com.cardenaspiero255.gamehubultra.domain.GamePlatform,
        displayName: String,
        publicId: String,
        alias: String? = null,
        avatarUrl: String? = null,
    ): ConnectedGameAccount
    suspend fun updatePublicMetadata(
        accountId: String,
        alias: String?,
        avatarUrl: String?,
    ): Boolean
    suspend fun remove(accountId: String)
}


/** Owns the persisted libraries synchronized from connected game stores. */
interface StoreLibraryStateRepository {
    fun getAll(): List<StoreLibraryGame>
    fun replaceForAccount(accountId: String, games: List<StoreLibraryGame>)
    fun removeForAccount(accountId: String)
}


/** Owns persisted optimization observations independently from Android UI consumers. */
interface GameOptimizationMemoryStateRepository {
    fun observationsFlow(contextKey: OptimizationContextKey): Flow<List<OptimizationObservation>>
    suspend fun record(contextKey: OptimizationContextKey, observation: OptimizationObservation)
    suspend fun pruneTo(contextKey: OptimizationContextKey)
    suspend fun clearAll()
    suspend fun clearGame(contextKey: OptimizationContextKey)
}
