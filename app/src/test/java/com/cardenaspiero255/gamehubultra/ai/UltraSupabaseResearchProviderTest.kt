package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UltraSupabaseResearchProviderTest {

    @Test
    fun parsesVerifiedEvidenceFromBackend() {
        val transport = object : UltraResearchBackendTransport {
            override fun post(
                endpoint: String,
                apiKey: String,
                body: String,
                timeoutMillis: Long
            ): String = """
                {
                  "claimKey":"weather",
                  "value":"22|clear",
                  "displayText":"22 °C y despejado",
                  "sourceId":"https://open-meteo.com/",
                  "authoritative":true
                }
            """.trimIndent()
        }

        val provider = SupabaseUltraResearchProvider(
            supabaseUrl = "https://example.supabase.co",
            publishableKey = "sb_publishable_test",
            transport = transport
        )

        val evidence = provider.fetch(
            UltraGeneralQueryRouter.classify("Ultra, clima de hoy en Santiago")
        )

        assertEquals("weather", evidence.claimKey)
        assertEquals("22|clear", evidence.value)
        assertEquals("22 °C y despejado", evidence.displayText)
        assertEquals("https://open-meteo.com/", evidence.sourceId)
        assertTrue(evidence.authoritative)
    }

    @Test
    fun backendAbstentionPreservesStructuredReasonInsteadOfThrowing() {
        val transport = object : UltraResearchBackendTransport {
            override fun post(
                endpoint: String,
                apiKey: String,
                body: String,
                timeoutMillis: Long
            ): String = """
                {
                  "abstained":true,
                  "reasonCode":"UPSTREAM_RATE_LIMIT",
                  "retryable":true,
                  "stage":"gemini_general",
                  "upstreamStatus":429,
                  "message":"El servicio está temporalmente ocupado."
                }
            """.trimIndent()
        }

        val provider = SupabaseUltraResearchProvider(
            supabaseUrl = "https://example.supabase.co",
            publishableKey = "sb_publishable_test",
            transport = transport
        )

        val result = provider.fetchResult(
            UltraGeneralQueryRouter.classify("Ultra, ¿qué es un motor?")
        )

        val abstained = assertIs<UltraProviderResult.Abstained>(result)
        assertEquals("UPSTREAM_RATE_LIMIT", abstained.reasonCode)
        assertEquals("gemini_general", abstained.stage)
        assertEquals(429, abstained.upstreamStatus)
        assertTrue(abstained.retryable)
    }

    @Test
    fun requestContractCarriesOptionalVerificationMode() {
        var body = ""
        val transport = object : UltraResearchBackendTransport {
            override fun post(
                endpoint: String,
                apiKey: String,
                bodyValue: String,
                timeoutMillis: Long
            ): String {
                body = bodyValue
                return """
                    {
                      "claimKey":"general:motor",
                      "value":"motor",
                      "displayText":"Un motor transforma energía.",
                      "sourceId":"https://es.wikipedia.org/wiki/Motor",
                      "authoritative":true
                    }
                """.trimIndent()
            }
        }

        SupabaseUltraResearchProvider(
            supabaseUrl = "https://example.supabase.co",
            publishableKey = "sb_publishable_test",
            transport = transport
        ).fetch(UltraGeneralQueryRouter.classify("Ultra, ¿qué es un motor?"))

        assertTrue(body.contains("\"verificationMode\":\"OPTIONAL\""))
    }

    @Test
    fun backendEndpointUsesUltraResearchFunction() {
        var endpoint = ""
        val transport = object : UltraResearchBackendTransport {
            override fun post(
                endpointValue: String,
                apiKey: String,
                body: String,
                timeoutMillis: Long
            ): String {
                endpoint = endpointValue
                return """
                    {
                      "claimKey":"weather",
                      "value":"20|cloudy",
                      "displayText":"20 °C y nublado",
                      "sourceId":"https://open-meteo.com/",
                      "authoritative":true
                    }
                """.trimIndent()
            }
        }

        SupabaseUltraResearchProvider(
            supabaseUrl = "https://example.supabase.co/",
            publishableKey = "sb_publishable_test",
            transport = transport
        ).fetch(UltraGeneralQueryRouter.classify("Ultra, clima de hoy en Rancagua"))

        assertEquals(
            "https://example.supabase.co/functions/v1/ultra-research",
            endpoint
        )
    }
}
