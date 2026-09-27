package com.cardenaspiero255.gamehubultra.ai

import com.cardenaspiero255.gamehubultra.BuildConfig

/**
 * Production composition root for Ultra chat queries.
 *
 * Provider construction stays here while query/fallback behavior lives behind
 * [UltraQueryExecutor], keeping UI and voice consumers independent from the
 * concrete research stack.
 */
object UltraProductionQueryExecutor : UltraQueryExecutor {
    private val productionProviders: List<UltraResearchProvider> =
        if (
            BuildConfig.SUPABASE_URL.isNotBlank() &&
            BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()
        ) {
            listOf(
                SupabaseUltraResearchProvider(
                    supabaseUrl = BuildConfig.SUPABASE_URL,
                    publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
                )
            )
        } else {
            emptyList()
        }

    private val delegate: UltraQueryExecutor = DefaultUltraQueryExecutor(
        coordinator = UltraQueryExecutionCoordinator(
            researchEngine = UltraVerifiedResearchEngine(
                providers = productionProviders
            )
        )
    )

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
