package com.cardenaspiero255.gamehubultra.ui.runtime

import com.cardenaspiero255.gamehubultra.UltraAssistantSessionMemory
import com.cardenaspiero255.gamehubultra.ai.UltraAgentRoutingGateway
import com.cardenaspiero255.gamehubultra.ai.UltraAssistantGateway
import com.cardenaspiero255.gamehubultra.ai.UltraNetworkGamingGateway
import com.cardenaspiero255.gamehubultra.ai.UltraQueryExecutor
import com.cardenaspiero255.gamehubultra.data.GameAliasStateRepository

internal data class UltraUiRuntimeDependencies(
    val queryExecutor: UltraQueryExecutor,
    val assistant: UltraAssistantGateway,
    val sessionMemory: UltraAssistantSessionMemory,
    val agentRouter: UltraAgentRoutingGateway,
    val networkGaming: UltraNetworkGamingGateway,
    val aliasRepository: GameAliasStateRepository
)
