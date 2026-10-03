package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WikimediaUltraResearchProviderTest {

    @Test
    fun stableDefinitionSurvivesSupabaseIndependentlyThroughWikimedia() {
        val transport = object : UltraPublicKnowledgeTransport {
            override fun get(
                url: String,
                timeoutMillis: Long
            ): UltraResearchHttpResponse =
                when {
                    url.contains("list=search") ->
                        UltraResearchHttpResponse(
                            statusCode = 200,
                            body = """
                                {
                                  "query":{
                                    "search":[{"title":"Motor"}]
                                  }
                                }
                            """.trimIndent()
                        )

                    url.contains("prop=extracts") ->
                        UltraResearchHttpResponse(
                            statusCode = 200,
                            body = """
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

                    else -> error("URL inesperada: $url")
                }
        }

        val result = WikimediaUltraResearchProvider(transport).fetchResult(
            UltraGeneralQueryRouter.classify("Ultra, ¿qué es un motor?")
        )

        val evidence = assertIs<UltraProviderResult.Evidence>(result).evidence
        assertEquals("Un motor es una máquina que transforma energía en movimiento o trabajo mecánico.", evidence.displayText)
        assertEquals("https://es.wikipedia.org/wiki/Motor", evidence.sourceId)
        assertTrue(evidence.authoritative)
    }

    @Test
    fun volatileQueriesNeverUseThePublicStableKnowledgeFallback() {
        var calls = 0
        val transport = object : UltraPublicKnowledgeTransport {
            override fun get(
                url: String,
                timeoutMillis: Long
            ): UltraResearchHttpResponse {
                calls++
                error("No debe consultar Wikimedia para datos volátiles")
            }
        }

        val result = WikimediaUltraResearchProvider(transport).fetchResult(
            UltraGeneralQueryRouter.classify("Ultra, ¿cuál es el precio actual de Bitcoin?")
        )

        val abstained = assertIs<UltraProviderResult.Abstained>(result)
        assertEquals("PUBLIC_FALLBACK_NOT_APPLICABLE", abstained.reasonCode)
        assertEquals(0, calls)
    }
}
