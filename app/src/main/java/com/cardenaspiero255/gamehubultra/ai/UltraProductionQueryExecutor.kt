package com.cardenaspiero255.gamehubultra.ai

/**
 * Production composition root for Ultra chat queries.
 *
 * Query/fallback behavior lives behind [UltraQueryExecutor], while concrete
 * research backend configuration is isolated behind
 * [UltraProductionResearchProviderSource].
 */
object UltraProductionQueryExecutor : UltraQueryExecutor {
    private val researchCache = UltraResearchCache()
    private val delegate: UltraQueryExecutor = DefaultUltraQueryExecutor(
        coordinator = UltraQueryExecutionCoordinator(
            researchGateway = UltraVerifiedResearchEngine(
                providers = UltraProductionResearchProviderSource.providers(),
                cache = researchCache
            )
        )
    )

    fun attachPersistentStore(store: UltraResearchPersistentStore) {
        researchCache.attachPersistentStore(store)
    }

    override fun answer(
        route: UltraAgentRoute.Chat,
        stableKnowledgeFallback: (() -> String?)?,
        localChat: () -> String
    ): String =
        delegate.answer(
            route = route,
            stableKnowledgeFallback = stableKnowledgeFallback,
            localChat = localChat
        )
}
