package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WikimediaUltraResearchProviderTest {

    @Test
    fun stableDefinitionSurvivesSupabaseIndependentlyThroughWikimedia() {
        val transport = scriptedTransport(
            searchBody = """
                {
                  "query":{
                    "search":[{"title":"Motor"}]
                  }
                }
            """.trimIndent(),
            extractBody = """
                {
                  "query":{
                    "pages":{
                      "123":{
                        "title":"Motor",
                        "extract":"Un motor es una máquina que transforma energía en movimiento o trabajo mecánico.",
                        "canonicalurl":"https://es.wikipedia.org/wiki/Motor"
                      }
                    }
                  }
                }
            """.trimIndent()
        )

        val result = WikimediaUltraResearchProvider(transport).fetchResult(
            UltraGeneralQueryRouter.classify("Ultra, ¿qué es un motor?")
        )

        val evidence = assertIs<UltraProviderResult.Evidence>(result).evidence
        assertEquals(
            "Un motor es una máquina que transforma energía en movimiento o trabajo mecánico.",
            evidence.displayText
        )
        assertEquals("https://es.wikipedia.org/wiki/Motor", evidence.sourceId)
        assertEquals("general:motor", evidence.claimKey)
        assertTrue(evidence.authoritative)
        assertEquals(1, evidence.independentSourceCount)
    }

    @Test
    fun directFetchReturnsEvidenceForStableKnowledge() {
        val provider = WikimediaUltraResearchProvider(
            scriptedTransport(
                searchBody = """{"query":{"search":[{"title":"Fotosíntesis"}]}}""",
                extractBody = """
                    {
                      "query":{
                        "pages":{
                          "1":{
                            "extract":"La fotosíntesis convierte energía luminosa en energía química.",
                            "canonicalurl":"https://es.wikipedia.org/wiki/Fotos%C3%ADntesis"
                          }
                        }
                      }
                    }
                """.trimIndent()
            )
        )

        val evidence = provider.fetch(
            UltraGeneralQueryRouter.classify("Ultra, ¿qué es la fotosíntesis?")
        )

        assertTrue(evidence.displayText.contains("fotosíntesis", ignoreCase = true))
        assertTrue(evidence.authoritative)
    }

    @Test
    fun volatileQueriesNeverUseThePublicStableKnowledgeFallback() {
        var calls = 0
        val transport = UltraPublicKnowledgeTransport { _, _ ->
            calls++
            error("No debe consultar Wikimedia para datos volátiles")
        }

        val result = WikimediaUltraResearchProvider(transport).fetchResult(
            UltraGeneralQueryRouter.classify(
                "Ultra, ¿cuál es el precio actual de Bitcoin?"
            )
        )

        val abstained = assertIs<UltraProviderResult.Abstained>(result)
        assertEquals("PUBLIC_FALLBACK_NOT_APPLICABLE", abstained.reasonCode)
        assertEquals(0, calls)
    }

    @Test
    fun blankStableTopicAbstainsWithoutCallingNetwork() {
        var calls = 0
        val provider = WikimediaUltraResearchProvider(
            UltraPublicKnowledgeTransport { _, _ ->
                calls++
                error("No debe consultar red")
            }
        )
        val request = UltraGeneralQueryRequest(
            originalText = "Ultra",
            kind = UltraGeneralQueryKind.GENERAL_KNOWLEDGE,
            requiresInternet = false,
            requiresFreshData = false,
            timeoutMillis = 4_000L,
            verificationMode = UltraVerificationMode.OPTIONAL
        )

        val result = provider.fetchResult(request)

        val abstained = assertIs<UltraProviderResult.Abstained>(result)
        assertEquals("PUBLIC_FALLBACK_EMPTY_QUERY", abstained.reasonCode)
        assertEquals(0, calls)
    }

    @Test
    fun searchNetworkFailureIsTypedAndRetryable() {
        val provider = WikimediaUltraResearchProvider(
            UltraPublicKnowledgeTransport { _, _ ->
                throw IllegalStateException("sin red")
            }
        )

        val result = provider.fetchResult(
            UltraGeneralQueryRouter.classify("Ultra, ¿qué es un motor?")
        )

        val failure = assertIs<UltraProviderResult.Failure>(result)
        assertEquals("PUBLIC_FALLBACK_NETWORK_FAILURE", failure.reasonCode)
        assertEquals("wikimedia-search", failure.stage)
        assertTrue(failure.retryable)
        assertEquals("sin red", failure.message)
    }

    @Test
    fun directFetchThrowsForTypedNetworkFailure() {
        val provider = WikimediaUltraResearchProvider(
            UltraPublicKnowledgeTransport { _, _ ->
                throw IllegalStateException("offline")
            }
        )

        val error = assertFailsWith<IllegalStateException> {
            provider.fetch(
                UltraGeneralQueryRouter.classify("Ultra, ¿qué es un motor?")
            )
        }

        assertTrue(error.message.orEmpty().contains("offline"))
    }

    @Test
    fun directFetchThrowsForAbstention() {
        val provider = WikimediaUltraResearchProvider(
            UltraPublicKnowledgeTransport { _, _ ->
                error("No debe llegar a la red")
            }
        )

        val error = assertFailsWith<IllegalStateException> {
            provider.fetch(
                UltraGeneralQueryRouter.classify(
                    "Ultra, ¿cuál es el clima actual en Rancagua?"
                )
            )
        }

        assertTrue(
            error.message.orEmpty()
                .contains("fallback", ignoreCase = true)
        )
    }

    @Test
    fun searchHttpStatusesPreserveStructuredFailureMetadata() {
        val expected = listOf(
            408 to "UPSTREAM_TIMEOUT",
            429 to "UPSTREAM_RATE_LIMIT",
            500 to "UPSTREAM_UNAVAILABLE",
            502 to "UPSTREAM_UNAVAILABLE",
            503 to "UPSTREAM_UNAVAILABLE",
            504 to "UPSTREAM_TIMEOUT",
            404 to "PUBLIC_FALLBACK_HTTP_FAILURE"
        )

        for ((status, reason) in expected) {
            val provider = WikimediaUltraResearchProvider(
                UltraPublicKnowledgeTransport { _, _ ->
                    UltraResearchHttpResponse(status, "error")
                }
            )

            val result = provider.fetchResult(
                UltraGeneralQueryRouter.classify("Ultra, ¿qué es un motor?")
            )

            val failure = assertIs<UltraProviderResult.Failure>(result)
            assertEquals(reason, failure.reasonCode, "HTTP $status")
            assertEquals(status, failure.upstreamStatus)
            assertEquals(
                status in setOf(408, 429, 500, 502, 503, 504),
                failure.retryable
            )
            assertEquals("wikimedia-search", failure.stage)
        }
    }

    @Test
    fun emptySearchResultAbstainsCleanly() {
        val provider = WikimediaUltraResearchProvider(
            UltraPublicKnowledgeTransport { url, _ ->
                assertTrue(url.contains("list=search"))
                UltraResearchHttpResponse(
                    200,
                    """{"query":{"search":[]}}"""
                )
            }
        )

        val result = provider.fetchResult(
            UltraGeneralQueryRouter.classify("Ultra, ¿qué es zxqv inexistente?")
        )

        val abstained = assertIs<UltraProviderResult.Abstained>(result)
        assertEquals("PUBLIC_FALLBACK_NO_RESULT", abstained.reasonCode)
        assertEquals("wikimedia-search", abstained.stage)
    }

    @Test
    fun extractNetworkFailurePreservesExtractStage() {
        var calls = 0
        val provider = WikimediaUltraResearchProvider(
            UltraPublicKnowledgeTransport { url, _ ->
                calls++
                if (url.contains("list=search")) {
                    UltraResearchHttpResponse(
                        200,
                        """{"query":{"search":[{"title":"Motor"}]}}"""
                    )
                } else {
                    throw IllegalArgumentException("extract offline")
                }
            }
        )

        val result = provider.fetchResult(
            UltraGeneralQueryRouter.classify("Ultra, ¿qué es un motor?")
        )

        val failure = assertIs<UltraProviderResult.Failure>(result)
        assertEquals(2, calls)
        assertEquals("PUBLIC_FALLBACK_NETWORK_FAILURE", failure.reasonCode)
        assertEquals("wikimedia-extract", failure.stage)
        assertTrue(failure.retryable)
    }

    @Test
    fun extractHttpFailureUsesTypedMetadata() {
        val provider = WikimediaUltraResearchProvider(
            scriptedTransport(
                searchBody = """{"query":{"search":[{"title":"Motor"}]}}""",
                extractStatus = 503,
                extractBody = "unavailable"
            )
        )

        val result = provider.fetchResult(
            UltraGeneralQueryRouter.classify("Ultra, ¿qué es un motor?")
        )

        val failure = assertIs<UltraProviderResult.Failure>(result)
        assertEquals("UPSTREAM_UNAVAILABLE", failure.reasonCode)
        assertEquals("wikimedia-extract", failure.stage)
        assertEquals(503, failure.upstreamStatus)
        assertTrue(failure.retryable)
    }

    @Test
    fun missingExtractAbstainsInsteadOfInventing() {
        val provider = WikimediaUltraResearchProvider(
            scriptedTransport(
                searchBody = """{"query":{"search":[{"title":"Motor"}]}}""",
                extractBody = """{"query":{"pages":{"1":{"title":"Motor"}}}}"""
            )
        )

        val result = provider.fetchResult(
            UltraGeneralQueryRouter.classify("Ultra, ¿qué es un motor?")
        )

        val abstained = assertIs<UltraProviderResult.Abstained>(result)
        assertEquals("PUBLIC_FALLBACK_NO_EVIDENCE", abstained.reasonCode)
        assertEquals("wikimedia-extract", abstained.stage)
    }

    @Test
    fun missingCanonicalUrlBuildsSafeWikipediaFallbackUrl() {
        val provider = WikimediaUltraResearchProvider(
            scriptedTransport(
                searchBody = """
                    {"query":{"search":[{"title":"Agujero negro"}]}}
                """.trimIndent(),
                extractBody = """
                    {
                      "query":{
                        "pages":{
                          "9":{
                            "extract":"Un agujero negro es una región del espacio con gravedad extrema."
                          }
                        }
                      }
                    }
                """.trimIndent()
            )
        )

        val result = provider.fetchResult(
            UltraGeneralQueryRouter.classify("Ultra, ¿qué es un agujero negro?")
        )

        val evidence = assertIs<UltraProviderResult.Evidence>(result).evidence
        assertEquals(
            "https://es.wikipedia.org/wiki/Agujero%20negro",
            evidence.sourceId
        )
        assertEquals("general:agujero-negro", evidence.claimKey)
    }

    @Test
    fun currentQuestionSuffixAndInvocationAreRemovedBeforeSearch() {
        var searchUrl = ""
        val provider = WikimediaUltraResearchProvider(
            UltraPublicKnowledgeTransport { url, _ ->
                if (url.contains("list=search")) {
                    searchUrl = url
                    UltraResearchHttpResponse(
                        200,
                        """{"query":{"search":[{"title":"Psicólogo"}]}}"""
                    )
                } else {
                    UltraResearchHttpResponse(
                        200,
                        """
                            {
                              "query":{
                                "pages":{
                                  "1":{
                                    "extract":"Un psicólogo es un profesional de la psicología.",
                                    "canonicalurl":"https://es.wikipedia.org/wiki/Psic%C3%B3logo"
                                  }
                                }
                              }
                            }
                        """.trimIndent()
                    )
                }
            }
        )
        val request = UltraGeneralQueryRouter
            .classify("Ultra, ¿qué es un psicólogo?")
            .copy(
                originalText =
                    "Contexto previo irrelevante. Pregunta actual: Ultra, ¿qué es un psicólogo?"
            )

        val result = provider.fetchResult(request)

        assertIs<UltraProviderResult.Evidence>(result)
        assertTrue(searchUrl.contains("srsearch=psic%C3%B3logo"))
        assertFalse(searchUrl.contains("Ultra"))
        assertFalse(searchUrl.contains("Pregunta"))
    }

    @Test
    fun escapedJsonContentIsDecodedCorrectly() {
        val provider = WikimediaUltraResearchProvider(
            scriptedTransport(
                searchBody =
                    """{"query":{"search":[{"title":"API"}]}}""",
                extractBody = """
                    {
                      "query":{
                        "pages":{
                          "1":{
                            "extract":"Una API permite comunicar sistemas.\nTambién puede incluir rutas como \/v1 y el carácter unicode \u00f1.",
                            "canonicalurl":"https:\/\/es.wikipedia.org\/wiki\/API"
                          }
                        }
                      }
                    }
                """.trimIndent()
            )
        )

        val result = provider.fetchResult(
            UltraGeneralQueryRouter.classify("Ultra, ¿qué es una API?")
        )

        val evidence = assertIs<UltraProviderResult.Evidence>(result).evidence
        assertTrue(evidence.displayText.contains("También"))
        assertTrue(evidence.displayText.contains("/v1"))
        assertTrue(evidence.displayText.contains("ñ"))
        assertEquals("https://es.wikipedia.org/wiki/API", evidence.sourceId)
    }

    @Test
    fun longExtractPrefersSentenceBoundaryWhenTrimming() {
        val firstSentence = "A".repeat(1_050) + ". "
        val secondSentence = "B".repeat(1_000)
        val provider = WikimediaUltraResearchProvider(
            scriptedTransport(
                searchBody =
                    """{"query":{"search":[{"title":"Tema largo"}]}}""",
                extractBody =
                    """{"query":{"pages":{"1":{"extract":"$firstSentence$secondSentence","canonicalurl":"https://example.com/tema"}}}}"""
            )
        )

        val result = provider.fetchResult(
            UltraGeneralQueryRouter.classify("Ultra, ¿qué es tema largo?")
        )

        val evidence = assertIs<UltraProviderResult.Evidence>(result).evidence
        assertEquals(firstSentence.trim(), evidence.displayText)
        assertTrue(evidence.displayText.length < 1_800)
    }

    @Test
    fun longExtractWithoutLateSentenceBoundaryUsesEllipsis() {
        val longText = "palabra ".repeat(400).trim()
        val provider = WikimediaUltraResearchProvider(
            scriptedTransport(
                searchBody =
                    """{"query":{"search":[{"title":"Tema continuo"}]}}""",
                extractBody =
                    """{"query":{"pages":{"1":{"extract":"$longText","canonicalurl":"https://example.com/continuo"}}}}"""
            )
        )

        val result = provider.fetchResult(
            UltraGeneralQueryRouter.classify("Ultra, ¿qué es tema continuo?")
        )

        val evidence = assertIs<UltraProviderResult.Evidence>(result).evidence
        assertTrue(evidence.displayText.endsWith("…"))
        assertTrue(evidence.displayText.length <= 1_801)
    }

    @Test
    fun accentedTitleNormalizesClaimKeyAndComparisonValue() {
        val provider = WikimediaUltraResearchProvider(
            scriptedTransport(
                searchBody =
                    """{"query":{"search":[{"title":"Fotosíntesis"}]}}""",
                extractBody = """
                    {
                      "query":{
                        "pages":{
                          "1":{
                            "extract":"La FOTOSÍNTESIS   convierte energía.",
                            "canonicalurl":"https://es.wikipedia.org/wiki/Fotos%C3%ADntesis"
                          }
                        }
                      }
                    }
                """.trimIndent()
            )
        )

        val result = provider.fetchResult(
            UltraGeneralQueryRouter.classify("Ultra, ¿qué es la fotosíntesis?")
        )

        val evidence = assertIs<UltraProviderResult.Evidence>(result).evidence
        assertEquals("general:fotosintesis", evidence.claimKey)
        assertEquals(
            "la fotosintesis convierte energia.",
            evidence.value
        )
    }

    private fun scriptedTransport(
        searchStatus: Int = 200,
        searchBody: String,
        extractStatus: Int = 200,
        extractBody: String = "{}"
    ): UltraPublicKnowledgeTransport =
        UltraPublicKnowledgeTransport { url, _ ->
            when {
                url.contains("list=search") ->
                    UltraResearchHttpResponse(searchStatus, searchBody)

                url.contains("prop=extracts") ->
                    UltraResearchHttpResponse(extractStatus, extractBody)

                else -> error("URL inesperada: $url")
            }
        }
}
