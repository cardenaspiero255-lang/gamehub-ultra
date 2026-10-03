package com.cardenaspiero255.gamehubultra.ai

import com.cardenaspiero255.gamehubultra.BuildConfig

/**
 * Supplies the research providers available to Ultra without exposing concrete
 * backend configuration to query execution.
 */
fun interface UltraResearchProviderSource {
    fun providers(): List<UltraResearchProvider>
}

/**
 * Configurable provider source used by production composition and tests.
 *
 * The private Supabase research backend remains the primary provider whenever
 * its public client configuration is present. A keyless Wikimedia provider is
 * always kept as a stable-knowledge fallback so ordinary definitions do not
 * depend on a single backend.
 */
class ConfiguredUltraResearchProviderSource(
    private val supabaseUrl: String,
    private val publishableKey: String,
    private val transport: UltraResearchBackendTransport =
        HttpUrlConnectionUltraResearchTransport,
    private val publicKnowledgeTransport: UltraPublicKnowledgeTransport =
        HttpUrlConnectionUltraPublicKnowledgeTransport
) : UltraResearchProviderSource {

    override fun providers(): List<UltraResearchProvider> =
        buildList {
            if (supabaseUrl.isNotBlank() && publishableKey.isNotBlank()) {
                add(
                    SupabaseUltraResearchProvider(
                        supabaseUrl = supabaseUrl,
                        publishableKey = publishableKey,
                        transport = transport
                    )
                )
            }

            add(
                WikimediaUltraResearchProvider(
                    transport = publicKnowledgeTransport
                )
            )
        }
}

/**
 * Android production composition for research providers.
 *
 * BuildConfig and concrete backend knowledge stop here.
 */
internal object UltraResearchProductionConfig {
    const val DEFAULT_SUPABASE_URL =
        "https://upkmszocqiqslrxuxevx.supabase.co"

    fun resolveSupabaseUrl(configuredUrl: String): String =
        configuredUrl.trim().ifBlank { DEFAULT_SUPABASE_URL }
}

object UltraProductionResearchProviderSource : UltraResearchProviderSource {
    private val delegate: UltraResearchProviderSource =
        ConfiguredUltraResearchProviderSource(
            supabaseUrl = UltraResearchProductionConfig.resolveSupabaseUrl(
                BuildConfig.SUPABASE_URL
            ),
            publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
        )

    override fun providers(): List<UltraResearchProvider> =
        delegate.providers()
}
