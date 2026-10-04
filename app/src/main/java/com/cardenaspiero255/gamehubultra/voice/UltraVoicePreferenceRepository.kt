package com.cardenaspiero255.gamehubultra.voice

internal interface UltraVoicePreferenceRepository {
    fun load(): UltraVoicePreferenceBundle
    fun save(bundle: UltraVoicePreferenceBundle)
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
}
