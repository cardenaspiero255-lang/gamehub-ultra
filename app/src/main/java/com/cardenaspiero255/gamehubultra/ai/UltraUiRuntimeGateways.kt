package com.cardenaspiero255.gamehubultra.ai

import com.cardenaspiero255.gamehubultra.network.NetworkGameProfile
import com.cardenaspiero255.gamehubultra.network.NetworkOptimizationOutcome
import com.cardenaspiero255.gamehubultra.voice.NaturalLanguageIntentResolver

internal data class UltraAgentRoutingRequest(
    val transcript: String,
    val optionalResolver: NaturalLanguageIntentResolver? = null,
    val telemetry: UltraRuntimeTelemetry? = null,
    val knownGameAliases: Set<String> = emptySet(),
    val conversationHistory: List<String> = emptyList()
)

internal fun interface UltraAgentRoutingGateway {
    fun route(request: UltraAgentRoutingRequest): UltraAgentRoute
}

internal interface UltraNetworkGamingGateway {
    fun execute(intent: UltraUtilityIntent.NetworkGamingControl): String

    fun applyProfile(profile: NetworkGameProfile): NetworkOptimizationOutcome
}


/**
 * Resolves utility answers that have a runtime side effect.
 *
 * Keeping this policy outside Compose prevents typed and spoken Ultra turns
 * from drifting into different behavior.
 */
internal object UltraUtilityRuntimeExecutor {
    fun execute(
        answer: UltraAgentAnswer,
        networkGaming: UltraNetworkGamingGateway
    ): String =
        when (val intent = answer.intent) {
            is UltraUtilityIntent.NetworkGamingControl ->
                networkGaming.execute(intent)
            else -> answer.message
        }

    fun executeIfCurrent(
        answer: UltraAgentAnswer,
        networkGaming: UltraNetworkGamingGateway,
        isCurrent: () -> Boolean
    ): String? {
        if (
            answer.intent is UltraUtilityIntent.NetworkGamingControl &&
            !isCurrent()
        ) {
            return null
        }
        return execute(answer, networkGaming)
    }
}
