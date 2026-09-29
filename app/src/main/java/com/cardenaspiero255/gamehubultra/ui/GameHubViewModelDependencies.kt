package com.cardenaspiero255.gamehubultra.ui

import com.cardenaspiero255.gamehubultra.data.GameLibraryStateRepository
import com.cardenaspiero255.gamehubultra.data.GameSelectionStateRepository

/**
 * Android-free composition boundary for dependencies consumed by [GameHubViewModel].
 *
 * Production wiring remains outside the ViewModel and will be migrated incrementally
 * by the following Block 9 cuts.
 */
fun interface GameHubViewModelDependencyFactory {
    fun create(): GameHubViewModelDependencies
}

/**
 * State boundaries consumed by [GameHubViewModel].
 *
 * Selection/profile and Library ownership are exposed as contracts rather than concrete
 * Android-backed repositories. Session ownership is migrated in the next Block 9 cut.
 */
interface GameHubViewModelDependencies :
    GameSelectionStateRepository,
    GameLibraryStateRepository
