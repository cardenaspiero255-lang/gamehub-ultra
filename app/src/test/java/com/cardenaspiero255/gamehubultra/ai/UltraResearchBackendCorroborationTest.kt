package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class UltraResearchBackendCorroborationTest {

    @Test
    fun backendCorroboratedSourcesProduceHighConfidence() {
        val provider = object : UltraResearchProvider {
            override val id = "verified-backend"

            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence =
                UltraResearchEvidence(
                    claimKey = "news:resident-evil",
                    value = "requiem-update",
                    displayText = "Hay una actualización verificada de Resident Evil.",
                    sourceId = "https://source-one.example/article",
                    supportingSourceIds = listOf(
                        "https://source-two.example/article"
                    ),
                    independentSourceCount = 2,
                    authoritative = false
                )
        }

        UltraVerifiedResearchEngine(listOf(provider)).use { engine ->
            val result = engine.answer(
                UltraGeneralQueryRouter.classify(
                    "Ultra, noticias de Resident Evil"
                )
            )

            assertFalse(result.abstained)
            assertEquals(UltraAnswerConfidence.HIGH, result.confidence)
            assertEquals(
                listOf(
                    "https://source-one.example/article",
                    "https://source-two.example/article"
                ),
                result.sources
            )
        }
    }

    @Test
    fun supabaseProviderPreservesBackendCorroborationMetadata() {
        val transport = object : UltraResearchBackendTransport {
            override fun post(
                endpoint: String,
                apiKey: String,
                body: String,
                timeoutMillis: Long
            ): String =
                """
                {
                  "claimKey":"spec:redmagic-11s-pro",
                  "value":"battery:7500",
                  "displayText":"REDMAGIC 11S Pro: batería de 7500 mAh.",
                  "sourceId":"https://global.redmagic.gg/products/redmagic-11s-pro",
                  "sourceIds":[
                    "https://global.redmagic.gg/products/redmagic-11s-pro",
                    "https://second.example/redmagic-11s-pro"
                  ],
                  "independentSourceCount":2,
                  "authoritative":true
                }
                """.trimIndent()
        }
        val provider = SupabaseUltraResearchProvider(
            supabaseUrl = "https://example.supabase.co",
            publishableKey = "publishable",
            transport = transport
        )

        val evidence = provider.fetch(
            UltraGeneralQueryRouter.classify(
                "Ultra, especificaciones actuales del RedMagic 11S Pro"
            )
        )

        assertEquals(2, evidence.independentSourceCount)
        assertEquals(
            listOf("https://second.example/redmagic-11s-pro"),
            evidence.supportingSourceIds
        )
    }
}
