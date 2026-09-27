package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UltraResearchProviderSourceTest {

    @Test
    fun incompleteCredentialsExposeNoExternalResearchProvider() {
        val source: UltraResearchProviderSource =
            ConfiguredUltraResearchProviderSource(
                supabaseUrl = "https://example.supabase.co",
                publishableKey = ""
            )

        assertTrue(source.providers().isEmpty())
    }

    @Test
    fun completeCredentialsExposeProviderThroughGenericResearchBoundary() {
        val source: UltraResearchProviderSource =
            ConfiguredUltraResearchProviderSource(
                supabaseUrl = "https://example.supabase.co",
                publishableKey = "test-publishable-key"
            )

        assertEquals(
            listOf("supabase-ultra-research"),
            source.providers().map { it.id }
        )
    }
}
