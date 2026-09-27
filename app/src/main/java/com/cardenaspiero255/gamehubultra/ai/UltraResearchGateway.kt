package com.cardenaspiero255.gamehubultra.ai

/**
 * Boundary for verified research used by Ultra coordinators.
 *
 * Callers that create a gateway own its lifecycle. Consumers receive the
 * gateway as a dependency and must not close it implicitly.
 */
interface UltraResearchGateway : AutoCloseable {
    fun answer(request: UltraGeneralQueryRequest): UltraVerifiedResearchResult

    override fun close() = Unit
}
