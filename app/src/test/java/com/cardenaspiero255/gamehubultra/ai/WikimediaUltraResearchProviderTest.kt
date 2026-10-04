package com.cardenaspiero255.gamehubultra.ai

import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
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
            "https://es.wikipedia.org/wiki/Agujero_negro",
            evidence.sourceId
        )
        assertEquals("general:agujero-negro", evidence.claimKey)
    }

    @Test
    fun englishDefinitionAcceptsTranslatedSpanishWikipediaTitle() {
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
                            "title":"Agujero negro",
                            "extract":"Un agujero negro es una región del espacio con un campo gravitatorio extremo.",
                            "canonicalurl":"https://es.wikipedia.org/wiki/Agujero_negro"
                          }
                        }
                      }
                    }
                """.trimIndent()
            )
        )

        val result = provider.fetchResult(
            UltraGeneralQueryRouter.classify("Ultra, what is a black hole?")
        )

        val evidence = assertIs<UltraProviderResult.Evidence>(result).evidence
        assertEquals("https://es.wikipedia.org/wiki/Agujero_negro", evidence.sourceId)
        assertTrue(evidence.displayText.contains("agujero negro", ignoreCase = true))
    }

    @Test
    fun properNameDefiniteArticleIsPreservedInSearchTopic() {
        var searchUrl = ""
        val provider = WikimediaUltraResearchProvider(
            UltraPublicKnowledgeTransport { url, _ ->
                if (url.contains("list=search")) {
                    searchUrl = url
                    UltraResearchHttpResponse(
                        200,
                        """{"query":{"search":[{"title":"El Niño"}]}}"""
                    )
                } else {
                    UltraResearchHttpResponse(
                        200,
                        """
                            {
                              "query":{
                                "pages":{
                                  "1":{
                                    "extract":"El Niño es un fenómeno climático del Pacífico tropical.",
                                    "canonicalurl":"https://es.wikipedia.org/wiki/El_Ni%C3%B1o"
                                  }
                                }
                              }
                            }
                        """.trimIndent()
                    )
                }
            }
        )

        val result = provider.fetchResult(
            UltraGeneralQueryRouter.classify("Ultra, ¿qué es El Niño?")
        )

        assertIs<UltraProviderResult.Evidence>(result)
        assertTrue(
            searchUrl.contains("srsearch=El+Ni%C3%B1o") ||
                searchUrl.contains("srsearch=El%20Ni%C3%B1o"),
            "La búsqueda debe conservar el artículo del nombre propio: $searchUrl"
        )
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


    @Test
    fun httpTransportReadsSuccessBodyAndSendsExpectedHeaders() {
        val requestLines = AtomicReference<List<String>>(emptyList())

        withRawHttpServer(
            status = 200,
            body = """{"ok":true}""",
            capturedRequestLines = requestLines
        ) { url ->
            val response = HttpUrlConnectionUltraPublicKnowledgeTransport.get(
                url,
                10L
            )

            assertEquals(200, response.statusCode)
            assertEquals("""{"ok":true}""", response.body)
        }

        val lines = requestLines.get()
        assertTrue(lines.firstOrNull()?.startsWith("GET ") == true)
        assertTrue(
            lines.any {
                it.equals("Accept: application/json", ignoreCase = true)
            }
        )
        assertTrue(
            lines.any {
                it.startsWith("User-Agent:", ignoreCase = true) &&
                    it.contains("GameHub-Ultra") &&
                    it.contains("github.com/cardenaspiero255-lang/gamehub-ultra")
            }
        )
    }

    @Test
    fun httpTransportReadsErrorStreamBody() {
        withRawHttpServer(
            status = 503,
            body = "temporal"
        ) { url ->
            val response = HttpUrlConnectionUltraPublicKnowledgeTransport.get(
                url,
                50_000L
            )

            assertEquals(503, response.statusCode)
            assertEquals("temporal", response.body)
        }
    }

    @Test
    fun parserSkipsMalformedTitleOccurrenceAndUsesNextValidString() {
        val provider = WikimediaUltraResearchProvider(
            scriptedTransport(
                searchBody =
                    """{"title" "rota","title":"Motor","query":{"search":[]}}""",
                extractBody =
                    """{"extract":"Un motor transforma energía.","canonicalurl":"https://example.com/motor"}"""
            )
        )

        val result = provider.fetchResult(
            UltraGeneralQueryRouter.classify("Ultra, ¿qué es un motor?")
        )

        val evidence = assertIs<UltraProviderResult.Evidence>(result).evidence
        assertEquals("Un motor transforma energía.", evidence.displayText)
    }

    @Test
    fun parserSkipsNonStringTitleAndUsesLaterValidTitle() {
        val provider = WikimediaUltraResearchProvider(
            scriptedTransport(
                searchBody =
                    """{"title":123,"title":"Motor"}""",
                extractBody =
                    """{"extract":"Un motor transforma energía.","canonicalurl":"https://example.com/motor"}"""
            )
        )

        val result = provider.fetchResult(
            UltraGeneralQueryRouter.classify("Ultra, ¿qué es un motor?")
        )

        assertIs<UltraProviderResult.Evidence>(result)
    }

    @Test
    fun malformedUnicodeEscapeAbstainsSafelyInsteadOfThrowing() {
        val provider = WikimediaUltraResearchProvider(
            scriptedTransport(
                searchBody = """{"title":"Motor"}""",
                extractBody = """{"extract":"Motor inválido \uZZZZ"}"""
            )
        )

        val result = provider.fetchResult(
            UltraGeneralQueryRouter.classify("Ultra, ¿qué es un motor?")
        )

        val abstained = assertIs<UltraProviderResult.Abstained>(result)
        assertEquals("PUBLIC_FALLBACK_NO_EVIDENCE", abstained.reasonCode)
    }

    @Test
    fun allSupportedJsonEscapesDecodeWithoutLosingTheAnswer() {
        val provider = WikimediaUltraResearchProvider(
            scriptedTransport(
                searchBody = """{"title":"API"}""",
                extractBody = """
                    {"extract":"API\tsegura\rcon\nsaltos y barra \\ y slash \/ y unicode \u00f1.","canonicalurl":"https:\/\/example.com\/api"}
                """.trimIndent()
            )
        )

        val result = provider.fetchResult(
            UltraGeneralQueryRouter.classify("Ultra, ¿qué es una API?")
        )

        val evidence = assertIs<UltraProviderResult.Evidence>(result).evidence
        assertTrue(evidence.displayText.contains("API segura con saltos"))
        assertTrue(evidence.displayText.contains("\\"))
        assertTrue(evidence.displayText.contains("/"))
        assertTrue(evidence.displayText.contains("ñ"))
        assertEquals("https://example.com/api", evidence.sourceId)
    }


    @Test
    fun offTopicSearchHitIsRejectedInsteadOfPresentedAsAuthoritative() {
        val provider = WikimediaUltraResearchProvider(
            scriptedTransport(
                searchBody =
                    """{"query":{"search":[{"title":"Gato doméstico"}]}}""",
                extractBody =
                    """{"query":{"pages":{"1":{"extract":"El gato doméstico es un mamífero.","canonicalurl":"https://es.wikipedia.org/wiki/Gato"}}}}"""
            )
        )

        val result = provider.fetchResult(
            UltraGeneralQueryRouter.classify("Ultra, ¿qué es un motor?")
        )

        val abstained = assertIs<UltraProviderResult.Abstained>(result)
        assertEquals("PUBLIC_FALLBACK_IRRELEVANT_RESULT", abstained.reasonCode)
    }

    @Test
    fun disambiguationPageIsRejected() {
        val provider = WikimediaUltraResearchProvider(
            scriptedTransport(
                searchBody =
                    """{"query":{"search":[{"title":"Mercurio"}]}}""",
                extractBody =
                    """{"query":{"pages":{"1":{"extract":"Mercurio puede referirse a varios conceptos.","canonicalurl":"https://es.wikipedia.org/wiki/Mercurio","pageprops":{"disambiguation":""}}}}}"""
            )
        )

        val request = UltraGeneralQueryRequest(
            originalText = "Ultra, ¿qué es Mercurio?",
            kind = UltraGeneralQueryKind.GENERAL_KNOWLEDGE,
            requiresInternet = false,
            requiresFreshData = false,
            timeoutMillis = 5_000L,
            verificationMode = UltraVerificationMode.OPTIONAL
        )

        val result = provider.fetchResult(request)

        val abstained = assertIs<UltraProviderResult.Abstained>(result)
        assertEquals("PUBLIC_FALLBACK_DISAMBIGUATION", abstained.reasonCode)
    }

    @Test
    fun dependentFollowUpWithoutExplicitSubjectDoesNotSearchWikipedia() {
        var calls = 0
        val provider = WikimediaUltraResearchProvider(
            UltraPublicKnowledgeTransport { _, _ ->
                calls++
                error("No debe consultar Wikimedia sin sujeto explícito")
            }
        )
        val request = UltraGeneralQueryRequest(
            originalText = "Ultra, ¿y cómo funciona eso?",
            kind = UltraGeneralQueryKind.GENERAL_KNOWLEDGE,
            requiresInternet = false,
            requiresFreshData = false,
            timeoutMillis = 5_000L,
            verificationMode = UltraVerificationMode.OPTIONAL
        )

        val result = provider.fetchResult(request)

        val abstained = assertIs<UltraProviderResult.Abstained>(result)
        assertEquals("PUBLIC_FALLBACK_CONTEXT_REQUIRED", abstained.reasonCode)
        assertEquals(0, calls)
    }

    @Test
    fun articleElWithExplicitSubjectDoesNotRequireContext() {
        var calls = 0
        val provider = WikimediaUltraResearchProvider(
            UltraPublicKnowledgeTransport { url, _ ->
                calls++
                if (url.contains("list=search")) {
                    UltraResearchHttpResponse(
                        200,
                        """{"query":{"search":[{"title":"ADN"}]}}"""
                    )
                } else {
                    UltraResearchHttpResponse(
                        200,
                        """{"query":{"pages":{"1":{"extract":"El ADN contiene información genética.","canonicalurl":"https://es.wikipedia.org/wiki/ADN"}}}}"""
                    )
                }
            }
        )

        val result = provider.fetchResult(
            UltraGeneralQueryRouter.classify("Ultra, ¿El ADN qué es?")
        )

        assertIs<UltraProviderResult.Evidence>(result)
        assertEquals(2, calls)
    }

    @Test
    fun accentedPronounElStillRequiresConversationContext() {
        var calls = 0
        val provider = WikimediaUltraResearchProvider(
            UltraPublicKnowledgeTransport { _, _ ->
                calls++
                error("No debe consultar red sin sujeto explícito")
            }
        )

        val result = provider.fetchResult(
            UltraGeneralQueryRouter.classify("Ultra, ¿él qué es?")
        )

        val abstained = assertIs<UltraProviderResult.Abstained>(result)
        assertEquals("PUBLIC_FALLBACK_CONTEXT_REQUIRED", abstained.reasonCode)
        assertEquals(0, calls)
    }

    @Test
    fun exactShortTitleMatchesBeforeMinimumTokenLengthFilter() {
        val provider = WikimediaUltraResearchProvider(
            scriptedTransport(
                searchBody = """{"query":{"search":[{"title":"pH"}]}}""",
                extractBody =
                    """{"query":{"pages":{"1":{"extract":"El pH expresa la acidez o alcalinidad de una disolución.","canonicalurl":"https://es.wikipedia.org/wiki/PH"}}}}"""
            )
        )

        val result = provider.fetchResult(
            UltraGeneralQueryRouter.classify("Ultra, ¿qué es pH?")
        )

        val evidence = assertIs<UltraProviderResult.Evidence>(result).evidence
        assertEquals("general:ph", evidence.claimKey)
        assertEquals("https://es.wikipedia.org/wiki/PH", evidence.sourceId)
    }

    @Test
    fun explicitSubjectAfterPronounDoesNotRequireConversationContext() {
        var calls = 0
        val provider = WikimediaUltraResearchProvider(
            UltraPublicKnowledgeTransport { url, _ ->
                calls++
                if (url.contains("list=search")) {
                    UltraResearchHttpResponse(
                        200,
                        """{"query":{"search":[{"title":"Fotosíntesis"}]}}"""
                    )
                } else {
                    UltraResearchHttpResponse(
                        200,
                        """{"query":{"pages":{"1":{"extract":"La fotosíntesis transforma energía luminosa en energía química.","canonicalurl":"https://es.wikipedia.org/wiki/Fotos%C3%ADntesis"}}}}"""
                    )
                }
            }
        )

        val result = provider.fetchResult(
            UltraGeneralQueryRouter.classify(
                "Ultra, ¿qué es eso de la fotosíntesis?"
            )
        )

        assertIs<UltraProviderResult.Evidence>(result)
        assertEquals(2, calls)
    }

    @Test
    fun providerCancellationDelegatesToPublicTransport() {
        val cancelledWorker = AtomicReference<Thread?>(null)
        val transport = object : UltraPublicKnowledgeTransport {
            override fun get(
                url: String,
                timeoutMillis: Long
            ): UltraResearchHttpResponse =
                UltraResearchHttpResponse(200, "{}")

            override fun cancelActiveRequest(worker: Thread) {
                cancelledWorker.set(worker)
            }
        }
        val provider = WikimediaUltraResearchProvider(transport)
        val worker = Thread.currentThread()

        provider.cancelActiveRequest(worker)

        assertEquals(worker, cancelledWorker.get())
    }

    @Test
    fun httpTransportCancellationDisconnectsBlockedRequest() {
        val server = ServerSocket(0)
        val accepted = CountDownLatch(1)
        val releaseServer = CountDownLatch(1)
        val serverThread = Thread {
            try {
                server.accept().use {
                    accepted.countDown()
                    releaseServer.await(3, TimeUnit.SECONDS)
                }
            } catch (_: Throwable) {
                // Cancellation or cleanup may close the socket/server.
            }
        }
        serverThread.isDaemon = true
        serverThread.start()

        val worker = Thread {
            runCatching {
                HttpUrlConnectionUltraPublicKnowledgeTransport.get(
                    "http://127.0.0.1:" + server.localPort + "/blocked",
                    5_000L
                )
            }
        }
        worker.start()

        try {
            assertTrue(
                accepted.await(1, TimeUnit.SECONDS),
                "El servidor debe aceptar la conexión antes de cancelarla."
            )

            HttpUrlConnectionUltraPublicKnowledgeTransport
                .cancelActiveRequest(worker)

            worker.join(700L)
            assertFalse(
                worker.isAlive,
                "Cancelar Wikimedia debe desconectar la petición HTTP bloqueada."
            )
        } finally {
            releaseServer.countDown()
            server.close()
            worker.interrupt()
            worker.join(2_000L)
            serverThread.join(2_000L)
        }
    }

    @Test
    fun httpTransportRejectsOversizedResponseBody() {
        val server = ServerSocket(0)
        val thread = Thread {
            try {
                server.accept().use { socket ->
                    val reader = socket.getInputStream()
                        .bufferedReader(Charsets.UTF_8)
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                    }
                    val headers = buildString {
                        append("HTTP/1.1 200 OK\r\n")
                        append("Content-Type: application/json\r\n")
                        append("Content-Length: 600000\r\n")
                        append("Connection: close\r\n\r\n")
                    }.toByteArray(Charsets.UTF_8)
                    socket.getOutputStream().use { output ->
                        output.write(headers)
                        output.flush()
                    }
                }
            } catch (_: Throwable) {
                // The client may close immediately after rejecting Content-Length.
            }
        }
        thread.isDaemon = true
        thread.start()

        try {
            val error = assertFailsWith<IllegalStateException> {
                HttpUrlConnectionUltraPublicKnowledgeTransport.get(
                    "http://127.0.0.1:" + server.localPort + "/oversized",
                    2_000L
                )
            }
            assertTrue(
                error.message.orEmpty().contains("large", ignoreCase = true) ||
                    error.message.orEmpty().contains("size", ignoreCase = true)
            )
        } finally {
            server.close()
            thread.join(3_000L)
        }
    }

    @Test
    fun httpTransportEnforcesTotalReadDeadlineWhileBytesKeepArriving() {
        val server = ServerSocket(0)
        val thread = Thread {
            try {
                server.accept().use { socket ->
                    val reader = socket.getInputStream()
                        .bufferedReader(Charsets.UTF_8)
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                    }

                    val output = socket.getOutputStream()
                    output.write(
                        buildString {
                            append("HTTP/1.1 200 OK\r\n")
                            append("Content-Type: application/json\r\n")
                            append("Content-Length: 1000\r\n")
                            append("Connection: close\r\n\r\n")
                        }.toByteArray(Charsets.UTF_8)
                    )
                    output.flush()

                    repeat(30) {
                        Thread.sleep(50L)
                        output.write('x'.code)
                        output.flush()
                    }
                }
            } catch (_: Throwable) {
                // Expected once the client enforces its total deadline.
            }
        }
        thread.isDaemon = true
        thread.start()

        val started = System.nanoTime()
        try {
            val error = assertFailsWith<IllegalStateException> {
                HttpUrlConnectionUltraPublicKnowledgeTransport.get(
                    "http://127.0.0.1:" + server.localPort + "/slow",
                    300L
                )
            }
            val elapsedMillis = java.util.concurrent.TimeUnit.NANOSECONDS
                .toMillis(System.nanoTime() - started)
            assertTrue(
                error.message.orEmpty().contains("deadline", ignoreCase = true)
            )
            assertTrue(
                elapsedMillis < 1_000L,
                "La lectura lenta superó el deadline total: ${elapsedMillis}ms"
            )
        } finally {
            server.close()
            thread.join(3_000L)
        }
    }

    @Test
    fun WikimediaCallsShareOneBoundedTimeoutBudget() {
        val observed = mutableListOf<Long>()
        val provider = WikimediaUltraResearchProvider(
            UltraPublicKnowledgeTransport { url, timeoutMillis ->
                observed += timeoutMillis
                if (url.contains("list=search")) {
                    Thread.sleep(80L)
                    UltraResearchHttpResponse(
                        200,
                        """{"query":{"search":[{"title":"Motor"}]}}"""
                    )
                } else {
                    UltraResearchHttpResponse(
                        200,
                        """{"query":{"pages":{"1":{"extract":"Un motor transforma energía en movimiento.","canonicalurl":"https://es.wikipedia.org/wiki/Motor"}}}}"""
                    )
                }
            }
        )
        val request = UltraGeneralQueryRouter
            .classify("Ultra, ¿qué es un motor?")
            .copy(timeoutMillis = 4_000L)

        val result = provider.fetchResult(request)

        assertIs<UltraProviderResult.Evidence>(result)
        assertEquals(2, observed.size)
        assertTrue(observed.all { it in 250L..1_200L })
        assertTrue(
            observed.sum() <= 2_400L,
            "Las dos llamadas no deben recibir el timeout completo: $observed"
        )
    }

    @Test
    fun spacedBrandQueryAcceptsCompactCanonicalTitle() {
        val provider = WikimediaUltraResearchProvider(
            scriptedTransport(
                searchBody =
                    """{"query":{"search":[{"title":"TikTok"}]}}""",
                extractBody =
                    """{"query":{"pages":{"1":{"extract":"TikTok es una plataforma de videos cortos.","canonicalurl":"https://es.wikipedia.org/wiki/TikTok"}}}}"""
            )
        )

        val result = provider.fetchResult(
            UltraGeneralQueryRouter.classify("Ultra, ¿Qué es tik Tok?")
        )

        val evidence = assertIs<UltraProviderResult.Evidence>(result).evidence
        assertTrue(evidence.displayText.contains("TikTok"))
        assertEquals("https://es.wikipedia.org/wiki/TikTok", evidence.sourceId)
    }

    private fun withRawHttpServer(
        status: Int,
        body: String,
        capturedRequestLines: AtomicReference<List<String>>? = null,
        block: (String) -> Unit
    ) {
        val server = ServerSocket(0)
        val serverFailure = AtomicReference<Throwable?>(null)
        val thread = Thread {
            try {
                server.accept().use { socket ->
                    val reader = socket.getInputStream()
                        .bufferedReader(Charsets.UTF_8)
                    val lines = mutableListOf<String>()
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                        lines += line
                    }
                    capturedRequestLines?.set(lines)

                    val bytes = body.toByteArray(Charsets.UTF_8)
                    val reason = if (status in 200..299) "OK" else "Error"
                    val headers = buildString {
                        append("HTTP/1.1 ")
                        append(status)
                        append(' ')
                        append(reason)
                        append("\r\n")
                        append("Content-Type: application/json\r\n")
                        append("Content-Length: ")
                        append(bytes.size)
                        append("\r\n")
                        append("Connection: close\r\n\r\n")
                    }.toByteArray(Charsets.UTF_8)

                    socket.getOutputStream().use { output ->
                        output.write(headers)
                        output.write(bytes)
                        output.flush()
                    }
                }
            } catch (error: Throwable) {
                if (!server.isClosed) {
                    serverFailure.set(error)
                }
            }
        }
        thread.isDaemon = true
        thread.start()

        try {
            block("http://127.0.0.1:" + server.localPort + "/test")
        } finally {
            server.close()
            thread.join(3_000L)
        }

        serverFailure.get()?.let { throw it }
        assertFalse(thread.isAlive, "El servidor HTTP de prueba no terminó")
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
