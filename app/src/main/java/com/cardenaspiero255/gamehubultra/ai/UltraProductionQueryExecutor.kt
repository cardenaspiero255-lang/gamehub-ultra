package com.cardenaspiero255.gamehubultra.ai

import com.cardenaspiero255.gamehubultra.BuildConfig

/**
 * Single production entry point for general chat queries.
 *
 * Stable questions stay on the local path. Queries that require current data
 * use the verified backend when Supabase is configured; otherwise Ultra
 * abstains instead of presenting stale local knowledge as current.
 */
object UltraProductionQueryExecutor {
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

    private val verifiedResearchEngine = UltraVerifiedResearchEngine(
        providers = productionProviders
    )
    private val coordinator = UltraQueryExecutionCoordinator(
        researchEngine = verifiedResearchEngine
    )

    fun answer(
        route: UltraAgentRoute.Chat,
        localChat: () -> String
    ): String {
        val request = route.query ?: return localChat()
        return coordinator.answer(
            request = request,
            localChat = localChat
        ).message
    }
}
