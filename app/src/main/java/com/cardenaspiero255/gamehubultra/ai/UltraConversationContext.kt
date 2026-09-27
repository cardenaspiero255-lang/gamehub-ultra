package com.cardenaspiero255.gamehubultra.ai

import com.cardenaspiero255.gamehubultra.voice.VoiceCommandParser

/**
 * Small in-memory context helper for a single Ultra conversation.
 * It keeps only the latest standalone user topic and expands ambiguous follow-ups.
 */
class UltraConversationContext {
    private var lastStandaloneTopic: String? = null

    fun observe(message: String) {
        val clean = message.trim()
        if (clean.isBlank()) return
        if (!UltraConversationContextResolver.looksLikeFollowUp(clean)) {
            lastStandaloneTopic = clean
        }
    }

    fun resolveFollowUp(message: String): String {
        val topic = lastStandaloneTopic ?: return message.trim()
        return UltraConversationContextResolver.resolve(
            message = message,
            conversation = listOf("Tú: $topic")
        )
    }
}

/**
 * Adds only the minimum recent user context needed for ambiguous follow-up
 * questions. The original visible user message is kept separate by callers.
 */
object UltraConversationContextResolver {
    private const val MAX_CONTEXT_CHARS = 600

    private val followUpPrefix = Regex(
        """^(y|and|pero|but|entonces|so|what about|y que tal|que tal|cual de los dos|which one)\b"""
    )
    private val followUpReference = Regex(
        """\b(ambos|ambas|los dos|las dos|ese|esa|esos|esas|el anterior|la anterior|su|sus|both|that one|those two|the previous one|its)\b"""
    )
    private val comparativeFollowUp = Regex(
        """^(cual|which)\b.*\b(mejor|better|mas|more|menos|less|tiene|has|seria|would|is)\b"""
    )

    fun resolve(
        message: String,
        conversation: List<String>
    ): String {
        val original = message.trim()
        if (original.isBlank() || !looksLikeFollowUp(original)) return original

        val previousUserTurn = conversation
            .asReversed()
            .asSequence()
            .mapNotNull(::userText)
            .firstOrNull { it.isNotBlank() }
            ?.takeLast(MAX_CONTEXT_CHARS)
            ?: return original

        return buildString {
            append("Contexto previo: ")
            append(previousUserTurn)
            append("\nPregunta actual: ")
            append(original)
        }
    }

    internal fun looksLikeFollowUp(message: String): Boolean {
        val clean = VoiceCommandParser.stripLeadingAssistantInvocation(message)
        if (clean.isBlank()) return false
        return followUpPrefix.containsMatchIn(clean) ||
            followUpReference.containsMatchIn(clean) ||
            comparativeFollowUp.containsMatchIn(clean)
    }

    internal fun referencesPriorEntity(message: String): Boolean {
        val clean = VoiceCommandParser.stripLeadingAssistantInvocation(message)
        if (clean.isBlank()) return false
        return followUpReference.containsMatchIn(clean) ||
            comparativeFollowUp.containsMatchIn(clean)
    }

    private fun userText(line: String): String? {
        val trimmed = line.trim()
        val prefixes = listOf("Tú:", "Tu:", "You:", "Usuario:", "User:")
        val prefix = prefixes.firstOrNull { trimmed.startsWith(it, ignoreCase = true) }
            ?: return null
        return trimmed.substring(prefix.length).trim()
    }
}
