package com.cardenaspiero255.gamehubultra

import android.content.Context
import com.cardenaspiero255.gamehubultra.data.GameHubPreferencesRepository
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

object GameSelectionStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun selectedGameFlow(context: Context): Flow<String?> =
        GameHubPreferencesRepository(context).selectedGameFlow()

    fun saveSelectedGame(context: Context, packageName: String) {
        scope.launch {
            GameHubPreferencesRepository(context).saveSelectedGame(packageName)
        }
    }

    fun saveSelectedGameAndProfile(
        context: Context,
        packageName: String,
        profile: PerformanceProfile
    ) {
        scope.launch {
            GameHubPreferencesRepository(context)
                .saveSelectedGameAndProfile(packageName, profile)
        }
    }
}
