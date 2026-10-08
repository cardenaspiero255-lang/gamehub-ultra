package com.cardenaspiero255.gamehubultra.ai

import com.cardenaspiero255.gamehubultra.tools.UltraToolContract
import com.cardenaspiero255.gamehubultra.tools.UltraToolDescriptor
import com.cardenaspiero255.gamehubultra.tools.UltraToolExecution
import com.cardenaspiero255.gamehubultra.tools.UltraToolKind
import com.cardenaspiero255.gamehubultra.tools.UltraToolResult
import com.cardenaspiero255.gamehubultra.tools.UltraToolSideEffect

/**
 * Boundary for verified research used by Ultra coordinators.
 *
 * Callers that create a gateway own its lifecycle. Consumers receive the
 * gateway as a dependency and must not close it implicitly.
 */
interface UltraResearchGateway :
    UltraToolContract<UltraGeneralQueryRequest, UltraVerifiedResearchResult>,
    AutoCloseable {

    override val descriptor: UltraToolDescriptor
        get() = UltraToolDescriptor(
            id = "ultra.research",
            kind = UltraToolKind.RESEARCH,
            sideEffect = UltraToolSideEffect.READ_ONLY,
            requiresNetwork = true
        )

    /**
     * True only when [researchProviderOffset] and provider budgets map to stable,
     * non-overlapping provider partitions. Generic gateways keep sequential
     * Frontier retry semantics by default.
     */
    val supportsProviderPartitioning: Boolean
        get() = false

    fun answer(request: UltraGeneralQueryRequest): UltraVerifiedResearchResult

    override fun execute(
        request: UltraGeneralQueryRequest
    ): UltraToolResult<UltraVerifiedResearchResult> =
        UltraToolExecution.protect(descriptor) {
            answer(request)
        }

    override fun close() = Unit
}
