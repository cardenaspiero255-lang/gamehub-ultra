package com.cardenaspiero255.gamehubultra.voice

import com.cardenaspiero255.gamehubultra.ai.UltraAgentRoute
import com.cardenaspiero255.gamehubultra.ai.UltraContextualQueryPlanner

/**
 * A personalized alias or contextual command must never bypass non-executable
 * cognition decisions. Only explicitly executable utterances reach the action
 * engine. This is deliberately independent of Android framework state.
 */
internal object UltraWakeRoutePrecedencePolicy {
    fun route(
        transcript: String,
        personalizedCommand: VoiceCommand?,
        contextualCommand: VoiceCommand,
        conversationHistory: List<String> = emptyList(),
        fallback: () -> UltraAgentRoute
    ): UltraAgentRoute {
        if (UltraCognitionIntentPolicy.isNonExecutableDiscussion(transcript)) {
            return UltraAgentRoute.Chat(
                message = transcript.trim(),
                query = UltraContextualQueryPlanner.plan(
                    message = transcript,
                    conversationHistory = conversationHistory
                )
            )
        }
        // An explicit network-mode instruction must not be treated as an
        // "activa <game>" launch merely because the verb is shared.
        // Keep negations, discussions and real game titles out of this override.
        if (UltraCompetitiveVoiceCommandPolicy.matches(transcript)) {
            return fallback()
        }
        return when {
            personalizedCommand != null -> UltraAgentRoute.Command(personalizedCommand)
            contextualCommand !is VoiceCommand.Unknown ->
                UltraAgentRoute.Command(contextualCommand)
            else -> fallback()
        }
    }
}
