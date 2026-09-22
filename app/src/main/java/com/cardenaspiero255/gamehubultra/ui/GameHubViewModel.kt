package com.cardenaspiero255.gamehubultra.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.cardenaspiero255.gamehubultra.data.GameHubPreferencesRepository
import com.cardenaspiero255.gamehubultra.domain.GameProfileConfig
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.domain.ThermalPreference
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class GameHubViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = GameHubPreferencesRepository(application)

    private val selectedGameFlow = repository.selectedGameFlow()
    private val selectedGameConfigFlow = selectedGameFlow.flatMapLatest { packageName ->
        packageName?.let(repository::gameProfileConfigFlow) ?: flowOf(null)
    }

    private val baseStateFlow = combine(
        repository.selectedProfileFlow(),
        selectedGameFlow,
        selectedGameConfigFlow,
        repository.favoriteGamesFlow()
    ) { globalProfile, selectedGamePackage, selectedGameConfig, favoriteGames ->
        BaseUiState(
            globalProfile = globalProfile,
            selectedGamePackage = selectedGamePackage,
            selectedGameConfig = selectedGameConfig,
            favoriteGames = favoriteGames
        )
    }

    val uiState = combine(
        baseStateFlow,
        repository.recentGamesFlow(),
        repository.manualGamesFlow()
    ) { base, recentGames, manualGames ->
        GameHubUiState(
            globalProfile = base.globalProfile,
            selectedGamePackage = base.selectedGamePackage,
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

    fun selectGlobalProfile(profile: PerformanceProfile) {
        viewModelScope.launch { repository.saveSelectedProfile(profile) }
    }

    fun selectGameProfile(packageName: String, profile: PerformanceProfile) {
        viewModelScope.launch {
            val current = repository.gameProfileConfigFlow(packageName).first()
                ?: GameProfileConfig()
            repository.saveGameProfileConfig(
                packageName,
                current.copy(performanceProfile = profile)
            )
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

    fun setFavoriteGame(packageName: String, favorite: Boolean) {
        viewModelScope.launch { repository.setFavoriteGame(packageName, favorite) }
    }

    fun recordRecentGame(packageName: String) {
        viewModelScope.launch { repository.recordRecentGame(packageName) }
    }

    fun setManualGame(packageName: String, manual: Boolean) {
        viewModelScope.launch { repository.setManualGame(packageName, manual) }
    }

    private data class BaseUiState(
        val globalProfile: PerformanceProfile,
        val selectedGamePackage: String?,
        val selectedGameConfig: GameProfileConfig?,
        val favoriteGames: Set<String>
    )
}
