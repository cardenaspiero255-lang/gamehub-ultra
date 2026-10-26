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
            Regex("^desde ahora te llamaras ([a-z]+)$"),
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

    /**
     * Match only the invocation token. Never normalize the full utterance:
     * normalize() strips decimal points, minus signs and math operators that
     * the downstream math and conversion engines need verbatim.
     */
    fun canonicalizeInvocation(transcript: String, currentName: String): String {
        val name = validate(currentName) ?: DEFAULT_NAME
        if (name == DEFAULT_NAME) return transcript

        val invocation = Regex(
            """^\s*(?:hey\s+)?([\p{L}]+)(?:\s*[,;:]\s*|\s+)""",
            RegexOption.IGNORE_CASE
        ).find(transcript) ?: return transcript

        val spokenName = VoiceCommandParser.normalize(invocation.groupValues[1])
        if (spokenName != name) return transcript
        val body = transcript.substring(invocation.range.last + 1).trimStart()
        return if (body.isNotBlank()) "ultra $body" else transcript
    }
}

internal class UltraWakeNameStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "ultra_wake_identity_v1", Context.MODE_PRIVATE
    )

    fun load(): String =
        UltraWakeNamePolicy.validate(
            runCatching { prefs.getString("active_name", null) }.getOrNull() ?: ""
        ) ?: UltraWakeNamePolicy.DEFAULT_NAME

    /** The immediately preceding distinct, user-selected wake name, if known. */
    fun previousName(): String? {
        val stored = runCatching { prefs.getString("previous_name", null) }.getOrNull()
        return stored?.let(UltraWakeNamePolicy::validate)
            ?.takeUnless { it == load() }
    }

    fun save(requested: String): Boolean {
        val safe = UltraWakeNamePolicy.validate(requested) ?: return false
        val current = load()
        // Repeating the same name is idempotent: don't corrupt name history.
        if (safe == current) return true
        // Commit both fields together before TTS confirms the change. Existing
        // installations with only active_name remain valid with unknown history.
        return prefs.edit()
            .putString("previous_name", current)
            .putString("active_name", safe)
            .commit()
    }
}

/**
 * Local identity operations used by both the foreground wake service and the
 * in-app voice/text chat. Never delegate settings mutations to conversational AI.
 */
internal object UltraWakeLocalIdentityActions {
    fun canonicalize(context: Context, transcript: String): String {
        val store = UltraWakeNameStore(context)
        return UltraWakeNamePolicy.canonicalizeInvocation(transcript, store.load())
    }

    fun handle(context: Context, transcript: String): String? {
        val store = UltraWakeNameStore(context)
        val canonical = UltraWakeNamePolicy.canonicalizeInvocation(transcript, store.load())
        UltraWakeNamePolicy.requestedName(canonical)?.let { requested ->
            return if (store.save(requested)) {
                "Entendido. Ahora me puedes llamar $requested."
            } else {
                "No pude guardar ese nombre. Sigo respondiendo con mi nombre anterior."
            }
        }
        return UltraWakeIdentityAnswerPolicy.respond(
            transcript = canonical,
            currentName = store.load(),
            previousName = store.previousName()
        )
    }
}
