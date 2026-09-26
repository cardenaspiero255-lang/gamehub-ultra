package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import kotlin.test.assertEquals
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
