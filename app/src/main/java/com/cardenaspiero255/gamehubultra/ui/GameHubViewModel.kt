package com.cardenaspiero255.gamehubultra.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cardenaspiero255.gamehubultra.data.GameHubPreferencesRepository
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GameHubViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = GameHubPreferencesRepository(application)

    private val selectedGameFlow = repository.selectedGameFlow()

    private val selectedGameProfileFlow = selectedGameFlow.flatMapLatest { packageName ->
        packageName?.let(repository::profileForGameFlow) ?: flowOf(null)
    }

    private val favoriteGamesFlow = repository.favoriteGamesFlow()
    private val recentGamesFlow = repository.recentGamesFlow()
    private val manualGamesFlow = repository.manualGamesFlow()

    val uiState = combine(
        repository.selectedProfileFlow(),
        selectedGameFlow,
        selectedGameProfileFlow,
        favoriteGamesFlow,
        recentGamesFlow,
        manualGamesFlow
    ) { globalProfile, selectedGamePackage, selectedGameProfile, favoriteGames, recentGames, manualGames ->
        GameHubUiState(
            globalProfile = globalProfile,
            selectedGamePackage = selectedGamePackage,
            selectedGameProfile = selectedGameProfile,
            favoriteGames = favoriteGames,
            recentGamePackages = recentGames,
            manualGamePackages = manualGames
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        GameHubUiState()
    )

    fun selectProfile(profile: PerformanceProfile) {
        viewModelScope.launch {
            repository.saveSelectedProfile(profile)
            uiState.value.selectedGamePackage?.let { packageName ->
                repository.saveProfileForGame(packageName, profile)
            }
        }
    }

    fun selectGame(packageName: String) {
        viewModelScope.launch {
            repository.saveSelectedGame(packageName)
        }
    }

    fun setFavoriteGame(packageName: String, favorite: Boolean) {
        viewModelScope.launch {
            repository.setFavoriteGame(packageName, favorite)
        }
    }

    fun recordRecentGame(packageName: String) {
        viewModelScope.launch {
            repository.recordRecentGame(packageName)
        }
    }

    fun setManualGame(packageName: String, manual: Boolean) {
        viewModelScope.launch {
            repository.setManualGame(packageName, manual)
        }
    }
}
