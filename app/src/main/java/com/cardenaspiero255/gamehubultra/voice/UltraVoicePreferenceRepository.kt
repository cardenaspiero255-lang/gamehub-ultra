package com.cardenaspiero255.gamehubultra.voice

internal interface UltraVoicePreferenceRepository {
    fun load(): UltraVoicePreferenceBundle
    fun save(bundle: UltraVoicePreferenceBundle)
    fun exportPreferences(): String
    fun importPreferences(serialized: String): Boolean
}

internal class CodecBackedUltraVoicePreferenceRepository(
    private val read: () -> String?,
    private val write: (String) -> Unit
) : UltraVoicePreferenceRepository {
    override fun load(): UltraVoicePreferenceBundle {
        val serialized = read()?.takeIf { it.isNotBlank() }
            ?: return UltraVoicePreferenceBundle()
        return UltraVoicePreferenceCodec.import(serialized)
            ?: UltraVoicePreferenceBundle()
    }

    override fun save(bundle: UltraVoicePreferenceBundle) {
        write(UltraVoicePreferenceCodec.export(bundle))
    }

    override fun exportPreferences(): String =
        UltraVoicePreferenceCodec.export(load())

    override fun importPreferences(serialized: String): Boolean {
        val validated = UltraVoicePreferenceCodec.import(serialized) ?: return false
        save(validated)
        return true
    }
}
