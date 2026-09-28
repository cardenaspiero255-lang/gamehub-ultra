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
