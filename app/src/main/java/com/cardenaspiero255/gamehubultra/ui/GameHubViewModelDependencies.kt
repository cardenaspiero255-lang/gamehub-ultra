package com.cardenaspiero255.gamehubultra.ui

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
 * Marker contract for the ViewModel dependency graph.
 *
 * Individual dependencies are introduced through this contract in subsequent cuts so
 * each migration remains independently testable and reviewable.
 */
interface GameHubViewModelDependencies
