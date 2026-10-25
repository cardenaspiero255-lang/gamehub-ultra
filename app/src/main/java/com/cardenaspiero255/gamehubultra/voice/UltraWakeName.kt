package com.cardenaspiero255.gamehubultra.voice

import android.content.Context

/**
 * User-controlled name for a consented, already-running listening session.
 * It is NOT speaker identification or a background microphone permission.
 */
internal object UltraWakeNamePolicy {
    const val DEFAULT_NAME = "ultra"

    private val rejected = setOf(
        "para", "stop", "detente", "callate", "silencio", "abre", "abrir",
        "activa", "activar", "apaga", "no", "modo", "perfil", "juego",
        "x4", "bs", "gamehub", "hey", "ayuda", "roberto abre bs"
    )

    fun validate(name: String): String? {
        val clean = VoiceCommandParser.normalize(name)
        if (clean == DEFAULT_NAME) return DEFAULT_NAME
        return clean.takeIf {
            it.matches(Regex("[a-z]{2,20}")) && it !in rejected
        }
    }

    /**
     * Require a direct instruction addressed to Ultra (or its current name).
     * A question, a negation or a quoted discussion cannot rename the service.
     */
    fun requestedName(transcript: String): String? {
        if (UltraCognitionIntentPolicy.isNonExecutableDiscussion(transcript)) return null
        val normalized = VoiceCommandParser.stripLeadingAssistantInvocation(transcript)
        val patterns = listOf(
            Regex("^(?:desde ahora )?te llamaras ([a-z]+)$"),
            Regex("^quiero que te llames ([a-z]+)$"),
            Regex("^tu nombre sera ([a-z]+)$"),
            Regex("^cambia tu nombre a ([a-z]+)$")
        )
        return patterns.firstNotNullOfOrNull { it.matchEntire(normalized)?.groupValues?.get(1) }
            ?.let(::validate)
    }

    /**
     * A new nickname must occur at the beginning, with an actual command
     * following it; mentions in the middle of someone else's words do not wake.
     * The original "Ultra" remains usable so the user can reset the nickname.
     */
    fun isInvoked(transcript: String, currentName: String): Boolean {
        val clean = VoiceCommandParser.normalize(transcript)
        val name = validate(currentName) ?: DEFAULT_NAME
        if (clean.startsWith("$name ") || clean.startsWith("hey $name ")) return true
        return clean.startsWith("ultra ") || clean.startsWith("hey ultra ") ||
            clean.startsWith("gamehub ultra ")
    }

    fun canonicalizeInvocation(transcript: String, currentName: String): String {
        val clean = VoiceCommandParser.normalize(transcript)
        val name = validate(currentName) ?: DEFAULT_NAME
        if (name == DEFAULT_NAME) return clean
        return when {
            clean.startsWith("$name ") -> "ultra " + clean.removePrefix("$name ")
            clean.startsWith("hey $name ") -> "ultra " + clean.removePrefix("hey $name ")
            else -> clean
        }
    }
}

internal class UltraWakeNameStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "ultra_wake_identity_v1", Context.MODE_PRIVATE
    )

    fun load(): String =
        UltraWakeNamePolicy.validate(prefs.getString("active_name", null) ?: "")
            ?: UltraWakeNamePolicy.DEFAULT_NAME

    fun save(requested: String): Boolean {
        val safe = UltraWakeNamePolicy.validate(requested) ?: return false
        return prefs.edit().putString("active_name", safe).commit()
    }
}
