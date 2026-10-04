package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UltraResearchProviderSourceTest {

    @Test
    fun incompleteCredentialsKeepNetworkAndOfflineStableKnowledgeFallbacksAvailable() {
        val source: UltraResearchProviderSource =
            ConfiguredUltraResearchProviderSource(
                supabaseUrl = "https://example.supabase.co",
                publishableKey = ""
            )

        assertEquals(
            listOf("wikimedia-public", "offline-stable-knowledge"),
            source.providers().map { it.id }
        )
    }

    @Test
    fun completeCredentialsKeepSupabasePrimaryAndIndependentFallbacksAvailable() {
        val source: UltraResearchProviderSource =
            ConfiguredUltraResearchProviderSource(
                supabaseUrl = "https://example.supabase.co",
                publishableKey = "test-publishable-key"
            )

        assertEquals(
            listOf(
                "supabase-ultra-research",
                "wikimedia-public",
                "offline-stable-knowledge"
            ),
            source.providers().map { it.id }
        )
    }

    @Test
    fun photosynthesisStillAnswersWhenEveryNetworkProviderIsOffline() {
        val source = ConfiguredUltraResearchProviderSource(
            supabaseUrl = "https://example.supabase.co",
            publishableKey = "test-publishable-key",
            transport = object : UltraResearchBackendTransport {
                override fun post(
                    endpoint: String,
                    apiKey: String,
                    body: String,
                    timeoutMillis: Long
                ): String = error("simulated backend network outage")
            },
            publicKnowledgeTransport = UltraPublicKnowledgeTransport { _, _ ->
                error("simulated public knowledge network outage")
            }
        )
        val engine = UltraVerifiedResearchEngine(source.providers())

        try {
            val result = engine.answer(
                UltraGeneralQueryRouter.classify(
                    "Ultra, ¿qué es la fotosíntesis?"
                )
            )

            assertFalse(result.abstained)
            assertTrue(
                result.message.contains("fotosíntesis", ignoreCase = true),
                result.message
            )
            assertTrue(result.fallbackUsed)
        } finally {
            engine.close()
        }
    }
}
