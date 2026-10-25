package com.cardenaspiero255.gamehubultra.voice

import android.content.Context

/**
 * A nickname changes the explicit *invocation prefix*, never the game/command grammar.
 * "Ultra" remains a recoverable fallback. Text that merely mentions a person's name
 * cannot wake the app; recognition remains behind the user's mic consent.
 */
internal object UltraAssistantIdentityPolicy {
    private const val DEFAULT_NAME = "ultra"
    private val validName = Regex("""[a-z]{2,20}""")
    private val reserved = setOf(
        "abre", "abrir", "activa", "activar", "para", "stop", "silencio",
        "modo", "perfil", "juego", "gaming", "gamehub", "competitivo",
        "balanceado", "x4", "ayuda", "nombre", "llamaras", "cambiar",
        "hola", "no", "si", "desde", "ahora", "roblox", "youtube"
    )
    private val rename = Regex(
        """^ultra (?:desde ahora te llamaras|a partir de ahora te llamaras|te llamaras|quiero llamarte) ([a-z]{2,20})$"""
    )
    private val reset = Regex(
        """^ultra (?:vuelve a llamarte|llamate|restablece tu nombre a|recupera el nombre) ultra$"""
    )

    fun valid(name: String): Boolean {
        val normalized = VoiceCommandParser.normalize(name)
        return validName.matches(normalized) &&
            (normalized == DEFAULT_NAME || normalized !in reserved)
    }

    /** Null means no exact leading invocation, NOT a generic chat request. */
    fun canonicalCommand(transcript: String, currentName: String): String? {
        val clean = VoiceCommandParser.normalize(transcript)
        val name = VoiceCommandParser.normalize(currentName)
            .takeIf(::valid) ?: DEFAULT_NAME
        val prefixes = listOf(
            "hey gamehub ultra ", "gamehub ultra ", "hey gamehub ", "gamehub ",
            "hey ultra ", "ultra ", "hey $name ", "$name "
        )
        val prefix = prefixes.firstOrNull(clean::startsWith) ?: return null
        val command = clean.removePrefix(prefix).trim()
        if (command.isEmpty()) return null
        return "ultra $command"
    }

    /** Returns only an explicitly named, valid change; never infer from conversation. */
    fun requestedName(transcript: String, currentName: String): String? {
        val command = canonicalCommand(transcript, currentName) ?: return null
        if (reset.matches(command)) return DEFAULT_NAME
        val proposed = rename.matchEntire(command)?.groupValues?.get(1) ?: return null
        return proposed.takeIf(::valid)
    }
}

/** A local-only, bounded preference; no voice samples, external account or server. */
internal class UltraAssistantNameStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "ultra_assistant_identity_v1", Context.MODE_PRIVATE
    )

    fun currentName(): String {
        val candidate = preferences.getString("name", null) ?: return "ultra"
        return candidate.takeIf(UltraAssistantIdentityPolicy::valid) ?: "ultra"
    }

    fun saveName(name: String): Boolean {
        val normalized = VoiceCommandParser.normalize(name)
        if (!UltraAssistantIdentityPolicy.valid(normalized)) return false
        // Synchronous durability before an audible acknowledgement; this runs
        // on the voice command executor, never on the Android main thread.
        return preferences.edit().putString("name", normalized).commit()
    }
}

/** Intent precedence for an affirmative, standalone network command only. */
internal object UltraCompetitiveVoiceCommandPolicy {
    private val activation = Regex(
        """^(?:activa|activar|habilita|habilitar|pon|ponme|usa|usar|aplica|aplicar|enable|activate|set|use)\s+(?:(?:el|la|the)\s+)?(?:modo|perfil|mode|profile)\s+(?:competitivo|competitive)(?:\s+(?:por favor|please|ahora|now))?$"""
    )

    fun matches(transcript: String): Boolean =
        activation.matches(VoiceCommandParser.stripLeadingAssistantInvocation(transcript))
}
