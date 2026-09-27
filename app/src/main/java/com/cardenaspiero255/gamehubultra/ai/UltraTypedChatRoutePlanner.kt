package com.cardenaspiero255.gamehubultra.ai

import com.cardenaspiero255.gamehubultra.voice.NaturalLanguageIntentResolver

/**
 * Routes typed Ultra input through the same unified intent pipeline used by voice.
 * This keeps commands, utilities, contextual research, and local chat behavior aligned.
 */
object UltraTypedChatRoutePlanner {
    fun route(
        message: String,
        conversationHistory: List<String> = emptyList(),
        optionalResolver: NaturalLanguageIntentResolver? = null,
        telemetry: UltraRuntimeTelemetry? = null,
        knownGameAliases: Set<String> = emptySet()
    ): UltraAgentRoute =
        UltraUnifiedAgentRouter.route(
            transcript = message.trim(),
            optionalResolver = optionalResolver,
            telemetry = telemetry,
            knownGameAliases = knownGameAliases,
            conversationHistory = conversationHistory
        )
}
