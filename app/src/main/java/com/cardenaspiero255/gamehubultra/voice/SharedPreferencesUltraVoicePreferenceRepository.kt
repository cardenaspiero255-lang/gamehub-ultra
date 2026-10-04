package com.cardenaspiero255.gamehubultra.voice

import android.content.Context

internal class SharedPreferencesUltraVoicePreferenceRepository(
    context: Context
) : UltraVoicePreferenceRepository {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )
    private val delegate = CodecBackedUltraVoicePreferenceRepository(
        read = { preferences.getString(KEY_BUNDLE, null) },
        write = { preferences.edit().putString(KEY_BUNDLE, it).apply() }
    )

    override fun load(): UltraVoicePreferenceBundle = delegate.load()

    override fun save(bundle: UltraVoicePreferenceBundle) = delegate.save(bundle)

    private companion object {
        const val PREFS_NAME = "ultra_voice_personalization"
        const val KEY_BUNDLE = "voice_preferences_v1"
    }
}
