package com.cardenaspiero255.gamehubultra

import android.content.Context
import com.cardenaspiero255.gamehubultra.data.GameHubPreferencesRepository
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

object ProfileSelectionStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun selectedProfileFlow(context: Context): Flow<PerformanceProfile> =
        GameHubPreferencesRepository(context).selectedProfileFlow()

    fun saveSelectedProfile(context: Context, profile: PerformanceProfile) {
        scope.launch {
            GameHubPreferencesRepository(context).saveSelectedProfile(profile)
        }
    }

    fun saveProfileForGame(
        context: Context,
        packageName: String,
        profile: PerformanceProfile
    ) {
        scope.launch {
            GameHubPreferencesRepository(context).saveProfileForGame(packageName, profile)
        }
    }
}
