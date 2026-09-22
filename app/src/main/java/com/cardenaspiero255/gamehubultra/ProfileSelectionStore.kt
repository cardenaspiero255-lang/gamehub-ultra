package com.cardenaspiero255.gamehubultra

import android.content.Context
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile

object ProfileSelectionStore {
    private const val PREFS_NAME = "gamehub_ultra"
    private const val KEY_SELECTED_PROFILE = "selected_profile"

    fun getSelectedProfile(context: Context): PerformanceProfile =
        PerformanceProfile.entries.firstOrNull {
            it.name == context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_SELECTED_PROFILE, PerformanceProfile.BALANCED.name)
        } ?: PerformanceProfile.BALANCED

    fun saveSelectedProfile(context: Context, profile: PerformanceProfile) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SELECTED_PROFILE, profile.name)
            .apply()
    }
}
