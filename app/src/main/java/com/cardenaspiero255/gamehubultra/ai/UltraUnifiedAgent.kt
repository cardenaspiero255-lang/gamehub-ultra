package com.cardenaspiero255.gamehubultra.ai

import com.cardenaspiero255.gamehubultra.voice.NaturalLanguageIntentResolver
import com.cardenaspiero255.gamehubultra.voice.VoiceCommand
import com.cardenaspiero255.gamehubultra.voice.VoiceCommandParser

sealed interface UltraAgentRoute {
    data class Command(val command: VoiceCommand) : UltraAgentRoute
    data class Chat(val message: String) : UltraAgentRoute
}

object UltraUnifiedAgentRouter {
    fun route(
        transcript: String,
        optionalResolver: NaturalLanguageIntentResolver? = null
    ): UltraAgentRoute {
        val command = VoiceCommandParser.parse(transcript, optionalResolver)
        return if (command is VoiceCommand.Unknown) {
            UltraAgentRoute.Chat(transcript.trim())
        } else {
            UltraAgentRoute.Command(command)
        }
    }
}

object UltraConversationPolicy {
    fun append(
        history: List<String>,
        entry: String,
        maxEntries: Int
    ): List<String> {
        val safeMaxEntries = maxEntries.coerceAtLeast(1)
        val cleanEntry = entry.trim()
        if (cleanEntry.isBlank()) return history.takeLast(safeMaxEntries)
        return (history + cleanEntry).takeLast(safeMaxEntries)
    }
}
