package com.cardenaspiero255.gamehubultra.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.cardenaspiero255.gamehubultra.data.DurableSelectionMutationQueue
import com.cardenaspiero255.gamehubultra.data.GameLibraryStateRepository
import com.cardenaspiero255.gamehubultra.data.GameSessionRecord
import com.cardenaspiero255.gamehubultra.data.GameSessionStateRepository
import com.cardenaspiero255.gamehubultra.data.RuntimeGameSession
import com.cardenaspiero255.gamehubultra.data.SessionEndMetrics
import com.cardenaspiero255.gamehubultra.data.SessionFinishHandle
import com.cardenaspiero255.gamehubultra.domain.GameProfileConfig
import com.cardenaspiero255.gamehubultra.domain.DEFAULT_ULTRA_PLAYER_NAME
import com.cardenaspiero255.gamehubultra.domain.OrientationPreference
import com.cardenaspiero255.gamehubultra.domain.SmartGameAssistantSuggestion
import com.cardenaspiero255.gamehubultra.domain.ResolutionTarget
import com.cardenaspiero255.gamehubultra.domain.PerformanceEvent
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.domain.ThermalPreference
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class GameHubViewModel(
    application: Application,
    dependencies: GameHubViewModelDependencies
) : AndroidViewModel(application) {
    private val repository = dependencies.selectionRepository
    private val libraryRepository: GameLibraryStateRepository = dependencies.libraryRepository
    private val performanceHistoryRepository = dependencies.performanceHistoryRepository
    private val playerIdentityRepository = dependencies.playerIdentityRepository
    private val sessionStore: GameSessionStateRepository = dependencies.sessionRepository
    private val sessionCoordinator =
        dependencies.sessionCoordinatorFactory.create(viewModelScope)

    val runtimeGameSession = sessionCoordinator.runtimeSession
    val sessionHistory = sessionStore.sessionsFlow()

    init {
        sessionCoordinator.recoverOrphans(System.currentTimeMillis())
    }

    fun beginRuntimeGameSession(record: GameSessionRecord) =
        sessionCoordinator.startSession(record)

    fun finishRuntimeGameSession(metrics: SessionEndMetrics): SessionFinishHandle? =
        sessionCoordinator.finishCurrent(metrics)

    fun clearSessionHistory() =
        sessionCoordinator.clearSessions()

    private val selectedGameFlow = repository.selectedGameFlow()
    private val playerNameFlow = playerIdentityRepository?.playerNameFlow()
        ?: flowOf(DEFAULT_ULTRA_PLAYER_NAME)
    private val selectedGameConfigFlow = selectedGameFlow.flatMapLatest { packageName ->
        packageName?.let(repository::gameProfileConfigFlow) ?: flowOf(null)
    }

    private val baseStateFlow = combine(
        repository.selectedProfileFlow(),
        selectedGameFlow,
        selectedGameConfigFlow,
        libraryRepository.favoriteGamesFlow(),
        playerNameFlow
    ) { globalProfile, selectedGamePackage, selectedGameConfig, favoriteGames, playerName ->
        BaseUiState(
            globalProfile = globalProfile,
            selectedGamePackage = selectedGamePackage,
            selectedGameHydrated = true,
            selectedGameConfig = selectedGameConfig,
            favoriteGames = favoriteGames,
            playerName = playerName
        )
    }

    val performanceHistory = performanceHistoryRepository.performanceHistoryFlow()

    val uiState = combine(
        baseStateFlow,
        libraryRepository.recentGamesFlow(),
        libraryRepository.manualGamesFlow()
    ) { base, recentGames, manualGames ->
        GameHubUiState(
            globalProfile = base.globalProfile,
            playerName = base.playerName,
            selectedGamePackage = base.selectedGamePackage,
            selectedGameHydrated = base.selectedGameHydrated,
            selectedGameConfig = base.selectedGameConfig,
            favoriteGames = base.favoriteGames,
            recentGamePackages = recentGames,
            manualGamePackages = manualGames
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        GameHubUiState()
    )

    suspend fun effectiveProfileForGame(packageName: String): PerformanceProfile =
        repository.profileForGameFlow(packageName).first()
            ?: repository.selectedProfileFlow().first()

    fun selectGlobalProfile(profile: PerformanceProfile) {
        viewModelScope.launch { repository.saveSelectedProfile(profile) }
    }

    fun updatePlayerName(rawName: String) {
        val identityRepository = playerIdentityRepository ?: return
        viewModelScope.launch { identityRepository.savePlayerName(rawName) }
    }

    fun saveGameProfileConfig(
        packageName: String,
        config: GameProfileConfig,
        onSaved: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.saveGameProfileConfig(packageName, config)
            onSaved()
        }
    }

    suspend fun persistGameProfile(
        packageName: String,
        profile: PerformanceProfile
    ) {
        val current = repository.gameProfileConfigFlow(packageName).first()
            ?: GameProfileConfig()
        repository.saveGameProfileConfig(
            packageName,
            current.copy(performanceProfile = profile)
        )
    }

    fun selectGameProfile(packageName: String, profile: PerformanceProfile) {
        viewModelScope.launch {
            persistGameProfile(packageName, profile)
        }
    }

    fun selectGameWithProfile(packageName: String, profile: PerformanceProfile) {
        viewModelScope.launch {
            repository.saveSelectedGameAndProfile(packageName, profile)
        }
    }

    fun selectGame(packageName: String) {
        viewModelScope.launch { repository.saveSelectedGame(packageName) }
    }

    fun persistVoiceSelectedGame(packageName: String) {
        DurableSelectionMutationQueue.enqueue(
            onFailure = ::reportVoiceSelectionPersistenceFailure
        ) {
            repository.saveSelectedGame(packageName)
        }
    }

    fun persistVoiceSelectedProfile(profile: PerformanceProfile) {
        DurableSelectionMutationQueue.enqueue(
            onFailure = ::reportVoiceSelectionPersistenceFailure
        ) {
            repository.saveSelectedProfile(profile)
        }
    }

    fun persistVoiceSelectedGameWithProfile(
        packageName: String,
        profile: PerformanceProfile
    ) {
        DurableSelectionMutationQueue.enqueue(
            onFailure = ::reportVoiceSelectionPersistenceFailure
        ) {
            repository.saveSelectedGameAndProfile(packageName, profile)
        }
    }

    fun setGameThermalPreference(
        packageName: String,
        preference: ThermalPreference
    ) {
        viewModelScope.launch {
            val current = repository.gameProfileConfigFlow(packageName).first()
                ?: GameProfileConfig()
            repository.saveGameProfileConfig(
                packageName,
                current.copy(thermalPreference = preference)
            )
        }
    }

    fun setGameRefreshRateTarget(packageName: String, targetHz: Int?) {
        viewModelScope.launch {
            val current = repository.gameProfileConfigFlow(packageName).first()
                ?: GameProfileConfig()
            repository.saveGameProfileConfig(
                packageName,
                current.copy(refreshRateTargetHz = targetHz)
            )
        }
    }

    fun setGameResolutionTarget(packageName: String, target: ResolutionTarget?) {
        viewModelScope.launch {
            val current = repository.gameProfileConfigFlow(packageName).first()
                ?: GameProfileConfig()
            repository.saveGameProfileConfig(
                packageName,
                current.copy(resolutionTarget = target)
            )
        }
    }

    fun setGameOrientationPreference(
        packageName: String,
        preference: OrientationPreference
    ) {
        viewModelScope.launch {
            val current = repository.gameProfileConfigFlow(packageName).first()
                ?: GameProfileConfig()
            repository.saveGameProfileConfig(
                packageName,
                current.copy(orientationPreference = preference)
            )
        }
    }

    fun applySmartGameAssistantSuggestion(
        packageName: String,
        suggestion: SmartGameAssistantSuggestion
    ) {
        viewModelScope.launch {
            val current = repository.gameProfileConfigFlow(packageName).first()
                ?: GameProfileConfig()
            repository.saveGameProfileConfig(
                packageName,
                current.copy(
                    performanceProfile = suggestion.profile,
                    thermalPreference = suggestion.thermalPreference,
                    refreshRateTargetHz = suggestion.refreshRateTargetHz
                )
            )
        }
    }

    fun setFavoriteGame(packageName: String, favorite: Boolean) {
        viewModelScope.launch { libraryRepository.setFavoriteGame(packageName, favorite) }
    }

    fun recordRecentGame(packageName: String) {
        viewModelScope.launch { libraryRepository.recordRecentGame(packageName) }
    }

    fun setManualGame(packageName: String, manual: Boolean) {
        viewModelScope.launch { libraryRepository.setManualGame(packageName, manual) }
    }

    suspend fun persistPerformanceEvent(event: PerformanceEvent) {
        performanceHistoryRepository.appendPerformanceEvent(event)
    }

    fun recordPerformanceEvent(event: PerformanceEvent) {
        viewModelScope.launch { persistPerformanceEvent(event) }
    }

    private fun reportVoiceSelectionPersistenceFailure(error: Throwable) {
        Log.e(
            "GameHubViewModel",
            "No se pudo persistir la selección o el perfil del asistente.",
            error
        )
    }

    private data class BaseUiState(
        val globalProfile: PerformanceProfile,
        val selectedGamePackage: String?,
        val selectedGameHydrated: Boolean,
        val selectedGameConfig: GameProfileConfig?,
        val favoriteGames: Set<String>,
        val playerName: String
    )
}
