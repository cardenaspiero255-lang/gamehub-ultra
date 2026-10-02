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
 * Incomplete credentials intentionally expose no external provider so callers
 * fall back to Ultra's verified abstention/local-policy behavior.
 */
class ConfiguredUltraResearchProviderSource(
    private val supabaseUrl: String,
    private val publishableKey: String,
    private val transport: UltraResearchBackendTransport =
        HttpUrlConnectionUltraResearchTransport
) : UltraResearchProviderSource {

    override fun providers(): List<UltraResearchProvider> =
        if (supabaseUrl.isBlank() || publishableKey.isBlank()) {
            emptyList()
        } else {
            listOf(
                SupabaseUltraResearchProvider(
                    supabaseUrl = supabaseUrl,
                    publishableKey = publishableKey,
                    transport = transport
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
    const val DEFAULT_SUPABASE_PUBLISHABLE_KEY =
        "sb_publishable_ApYyZYZoVTV1-UwO2ts5Iw_H_TAUCMB"

    fun resolveSupabaseUrl(configuredUrl: String): String =
        configuredUrl.trim().ifBlank { DEFAULT_SUPABASE_URL }

    fun resolvePublishableKey(configuredKey: String): String =
        configuredKey.trim().ifBlank { DEFAULT_SUPABASE_PUBLISHABLE_KEY }
}

object UltraProductionResearchProviderSource : UltraResearchProviderSource {
    private val delegate: UltraResearchProviderSource =
        ConfiguredUltraResearchProviderSource(
            supabaseUrl = UltraResearchProductionConfig.resolveSupabaseUrl(
                BuildConfig.SUPABASE_URL
            ),
            publishableKey = UltraResearchProductionConfig.resolvePublishableKey(
                BuildConfig.SUPABASE_PUBLISHABLE_KEY
            )
        )

    override fun providers(): List<UltraResearchProvider> =
        delegate.providers()
}
