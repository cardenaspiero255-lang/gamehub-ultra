package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import kotlin.test.assertEquals

class UltraResearchProviderSourceTest {

    @Test
    fun incompleteCredentialsKeepPublicStableKnowledgeProviderAvailable() {
        val source: UltraResearchProviderSource =
            ConfiguredUltraResearchProviderSource(
                supabaseUrl = "https://example.supabase.co",
                publishableKey = ""
            )

        assertEquals(
            listOf("wikimedia-public"),
            source.providers().map { it.id }
        )
    }

    @Test
    fun completeCredentialsKeepSupabasePrimaryAndPublicFallbackAvailable() {
        val source: UltraResearchProviderSource =
            ConfiguredUltraResearchProviderSource(
                supabaseUrl = "https://example.supabase.co",
                publishableKey = "test-publishable-key"
            )

        assertEquals(
            listOf("supabase-ultra-research", "wikimedia-public"),
            source.providers().map { it.id }
        )
    }
}
