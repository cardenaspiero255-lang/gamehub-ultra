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
    fun cancelActiveRequestDelegatesToTransportWorker() {
        val entered = java.util.concurrent.CountDownLatch(1)
        val released = java.util.concurrent.CountDownLatch(1)
        var cancelledThread: Thread? = null
        val transport = object : UltraResearchBackendTransport {
            override fun post(
                endpoint: String,
                apiKey: String,
                body: String,
                timeoutMillis: Long
            ): String = error("unused")

            override fun postResponse(
                endpoint: String,
                apiKey: String,
                body: String,
                timeoutMillis: Long
            ): UltraResearchHttpResponse {
                entered.countDown()
                while (released.count > 0L) {
                    try {
                        released.await()
                    } catch (_: InterruptedException) {
                        // Simula una lectura de red que no se libera con interrupt().
                    }
                }
                return UltraResearchHttpResponse(
                    503,
                    """{"abstained":true,"reasonCode":"UPSTREAM_UNAVAILABLE"}"""
                )
            }

            override fun cancelRequest(worker: Thread) {
                cancelledThread = worker
                released.countDown()
            }
        }
        val provider = SupabaseUltraResearchProvider(
            supabaseUrl = "https://example.supabase.co",
            publishableKey = "sb_publishable_test",
            transport = transport
        )
        val worker = Thread {
            provider.fetchResult(
                UltraGeneralQueryRouter.classify("Ultra, ¿qué es un motor?")
            )
        }

        try {
            worker.start()
            assertTrue(entered.await(1, java.util.concurrent.TimeUnit.SECONDS))
            provider.cancelActiveRequest(worker)
            worker.join(1_000L)

            assertEquals(worker, cancelledThread)
            assertTrue(!worker.isAlive)
        } finally {
            released.countDown()
            worker.join(1_000L)
        }
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

    @Test
    fun transientClientNetworkFailureRetriesOnceAndRecovers() {
        var calls = 0
        val transport = object : UltraResearchBackendTransport {
            override fun post(
                endpoint: String,
                apiKey: String,
                body: String,
                timeoutMillis: Long
            ): String = error("unused")

            override fun postResponse(
                endpoint: String,
                apiKey: String,
                body: String,
                timeoutMillis: Long
            ): UltraResearchHttpResponse {
                calls += 1
                if (calls == 1) {
                    throw java.io.IOException("temporary client network failure")
                }
                return UltraResearchHttpResponse(
                    statusCode = 200,
                    body = """
                        {
                          "claimKey":"general:photosynthesis",
                          "value":"photosynthesis",
                          "displayText":"La fotosíntesis transforma energía de la luz en energía química.",
                          "sourceId":"https://es.wikipedia.org/wiki/Fotos%C3%ADntesis",
                          "authoritative":true
                        }
                    """.trimIndent()
                )
            }
        }
        val provider = SupabaseUltraResearchProvider(
            supabaseUrl = "https://example.supabase.co",
            publishableKey = "sb_publishable_test",
            transport = transport
        )

        val result = provider.fetchResult(
            UltraGeneralQueryRouter.classify("Ultra, ¿qué es la fotosíntesis?")
        )

        assertIs<UltraProviderResult.Evidence>(result)
        assertEquals(2, calls)
    }

    @Test
    fun nonRetryableClientHttpFailureIsNotRetried() {
        var calls = 0
        val transport = object : UltraResearchBackendTransport {
            override fun post(
                endpoint: String,
                apiKey: String,
                body: String,
                timeoutMillis: Long
            ): String = error("unused")

            override fun postResponse(
                endpoint: String,
                apiKey: String,
                body: String,
                timeoutMillis: Long
            ): UltraResearchHttpResponse {
                calls += 1
                return UltraResearchHttpResponse(
                    statusCode = 400,
                    body = """{"abstained":true,"reasonCode":"BAD_REQUEST"}"""
                )
            }
        }
        val provider = SupabaseUltraResearchProvider(
            supabaseUrl = "https://example.supabase.co",
            publishableKey = "sb_publishable_test",
            transport = transport
        )

        provider.fetchResult(
            UltraGeneralQueryRouter.classify("Ultra, ¿qué es la fotosíntesis?")
        )

        assertEquals(1, calls)
    }

}
