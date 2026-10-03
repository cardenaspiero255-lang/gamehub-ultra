package com.cardenaspiero255.gamehubultra.ui

import com.cardenaspiero255.gamehubultra.data.GameLibraryStateRepository
import com.cardenaspiero255.gamehubultra.data.GameSelectionStateRepository
import com.cardenaspiero255.gamehubultra.data.GameSessionStateRepository
import com.cardenaspiero255.gamehubultra.data.GameSessionLifecycleCoordinatorFactory
import com.cardenaspiero255.gamehubultra.data.PerformanceHistoryStateRepository
import com.cardenaspiero255.gamehubultra.data.PlayerIdentityStateRepository

/**
 * Android-free composition boundary for dependencies consumed by [GameHubViewModel].
 *
 * Production wiring remains outside the ViewModel and is migrated incrementally by Block 9.
 */
fun interface GameHubViewModelDependencyFactory {
    fun create(): GameHubViewModelDependencies
}


/**
 * Explicit state bundle consumed by [GameHubViewModel].
 *
 * Persistent ownership stays behind dedicated contracts so the ViewModel never constructs
 * Android-backed repository implementations itself.
 */
data class GameHubViewModelDependencies(
    val selectionRepository: GameSelectionStateRepository,
    val libraryRepository: GameLibraryStateRepository,
    val performanceHistoryRepository: PerformanceHistoryStateRepository,
    val sessionRepository: GameSessionStateRepository,
    val sessionCoordinatorFactory: GameSessionLifecycleCoordinatorFactory,
    val playerIdentityRepository: PlayerIdentityStateRepository? = null,
)
