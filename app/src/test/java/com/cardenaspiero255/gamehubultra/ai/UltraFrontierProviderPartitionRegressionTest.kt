package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UltraFrontierProviderPartitionRegressionTest {
    @Test
    fun failedFirstBranchMustNotReselectItsProviderAfterAdaptiveRankingChanges() {
        val calls = mutableListOf<String>()
        val failed = object : UltraResearchProvider {
            override val id = "supabase"
            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence =
                error("Not called")
            override fun fetchResult(request: UltraGeneralQueryRequest): UltraProviderResult {
                synchronized(calls) { calls += id }
                return UltraProviderResult.Failure(
                    reasonCode = "UPSTREAM_UNAVAILABLE",
                    retryable = false
                )
            }
        }
        val fallback = object : UltraResearchProvider {
            override val id = "wikimedia"
            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence {
                synchronized(calls) { calls += id }
                return UltraResearchEvidence(
                    claimKey = "general:planet",
                    value = "planet",
                    displayText = "Un planeta orbita una estrella.",
                    sourceId = "https://www.wikidata.org/wiki/Q634",
                    authoritative = true
                )
            }
        }
        UltraVerifiedResearchEngine(listOf(failed, fallback)).use { engine ->
            val base = UltraGeneralQueryRouter.classify("¿Qué es un planeta?")
            val first = engine.answer(
                base.copy(researchProviderOffset = 0, researchProviderBudget = 1)
            )
            assertTrue(first.abstained)

            // The first failure demotes Supabase. The offset must still select
            // Wikimedia, not the already-tried Supabase.
            val second = engine.answer(
                base.copy(researchProviderOffset = 1, researchProviderBudget = 1)
            )
            assertEquals(listOf("supabase", "wikimedia"), calls)
            assertFalse(second.abstained)
            assertEquals("Un planeta orbita una estrella.", second.message)
        }
    }

    @Test
    fun providerCooldownCannotShiftAnotherBranchProviderIdentity() {
        val calls = mutableListOf<String>()
        val first = object : UltraResearchProvider {
            override val id = "supabase"
            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence =
                error("Not called")
            override fun fetchResult(request: UltraGeneralQueryRequest): UltraProviderResult {
                synchronized(calls) { calls += id }
                return UltraProviderResult.Failure("UPSTREAM_UNAVAILABLE", retryable = false)
            }
        }
        val second = object : UltraResearchProvider {
            override val id = "wikimedia"
            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence {
                synchronized(calls) { calls += id }
                return UltraResearchEvidence(
                    claimKey = "general:planet",
                    value = "planet",
                    displayText = "Un planeta orbita una estrella.",
                    sourceId = "https://www.wikidata.org/wiki/Q634"
                )
            }
        }
        val health = UltraResearchProviderHealth(
            UltraResearchProviderHealthPolicy(failureThreshold = 1)
        )
        UltraVerifiedResearchEngine(
            providers = listOf(first, second),
            providerHealth = health
        ).use { engine ->
            val query = UltraGeneralQueryRouter.classify("¿Qué es un planeta?")
            engine.answer(query.copy(researchProviderOffset = 0, researchProviderBudget = 1))
            val answer = engine.answer(
                query.copy(researchProviderOffset = 1, researchProviderBudget = 1)
            )
            assertEquals(listOf("supabase", "wikimedia"), calls)
            assertFalse(answer.abstained)
        }
    }

    @Test
    fun cooledDownProviderSlotRemainsRetryableWhenAnotherSlotExists() {
        val bad = object : UltraResearchProvider {
            override val id = "failed-provider"
            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence =
                error("Not used")
            override fun fetchResult(request: UltraGeneralQueryRequest): UltraProviderResult =
                UltraProviderResult.Failure("UPSTREAM_UNAVAILABLE", retryable = false)
        }
        val fresh = object : UltraResearchProvider {
            override val id = "healthy-provider"
            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence =
                UltraResearchEvidence(
                    claimKey = "general:test",
                    value = "fact",
                    displayText = "Hecho verificado.",
                    sourceId = "https://example.org/fact"
                )
        }
        val health = UltraResearchProviderHealth(
            UltraResearchProviderHealthPolicy(failureThreshold = 1)
        )
        UltraVerifiedResearchEngine(listOf(bad, fresh), providerHealth = health).use {
            val query = UltraGeneralQueryRouter.classify("¿Qué es un planeta?")
                .copy(researchProviderBudget = 1, researchProviderOffset = 0)
            it.answer(query)
            val quarantined = it.answer(query)
            assertEquals("PROVIDERS_COOLDOWN", quarantined.reasonCode)
            assertTrue(quarantined.retryable)
            assertFalse(it.answer(query.copy(researchProviderOffset = 1)).abstained)
        }
    }

    @Test
    fun androidAcceptsLocalEvidenceCorroboratedByOnlySourceIds() {
        val url = "https://example.org/corroboration"
        val transport = object : UltraResearchBackendTransport {
            override fun post(
                endpoint: String,
                apiKey: String,
                body: String,
                timeoutMillis: Long
            ): String = """
                {
                  "engineVersion":"20",
                  "claimKey":"local-stable:planet",
                  "value":"planet",
                  "displayText":"Un planeta orbita una estrella.",
                  "sourceIds":["$url"],
                  "independentSourceCount":1,
                  "authoritative":false
                }
            """.trimIndent()
        }
        val provider = SupabaseUltraResearchProvider(
            supabaseUrl = "https://example.supabase.co",
            publishableKey = "sb_publishable_test",
            transport = transport,
            requiredEngineVersion = UltraResearchProtocol.ENGINE_VERSION
        )
        val evidence = assertIs<UltraProviderResult.Evidence>(
            provider.fetchResult(UltraGeneralQueryRouter.classify("¿Qué es un planeta?"))
        ).evidence
        assertEquals(url, evidence.sourceId)
        assertEquals(listOf(url), listOf(evidence.sourceId) + evidence.supportingSourceIds)
        assertEquals(1, evidence.independentSourceCount)
        assertFalse(evidence.authoritative)
    }

    @Test
    fun duplicateCorroborationUrlsCannotClaimTwoIndependentSources() {
        val url = "https://example.org/one-source"
        val transport = object : UltraResearchBackendTransport {
            override fun post(
                endpoint: String,
                apiKey: String,
                body: String,
                timeoutMillis: Long
            ): String = """
                {
                  "engineVersion":"20",
                  "claimKey":"general:planet",
                  "value":"planet",
                  "displayText":"Una definición corroborada.",
                  "sourceIds":["$url","$url","  $url  "],
                  "independentSourceCount":2,
                  "authoritative":true
                }
            """.trimIndent()
        }
        val provider = SupabaseUltraResearchProvider(
            supabaseUrl = "https://example.supabase.co",
            publishableKey = "sb_publishable_test",
            transport = transport,
            requiredEngineVersion = UltraResearchProtocol.ENGINE_VERSION
        )
        val evidence = assertIs<UltraProviderResult.Evidence>(
            provider.fetchResult(UltraGeneralQueryRouter.classify("¿Qué es un planeta?"))
        ).evidence
        assertEquals(1, evidence.independentSourceCount)
        assertEquals(listOf(url), listOf(evidence.sourceId) + evidence.supportingSourceIds)
    }

    @Test
    fun optionalTerminologyWithoutSourcesRemainsUsableButUnverified() {
        val transport = object : UltraResearchBackendTransport {
            override fun post(
                endpoint: String,
                apiKey: String,
                body: String,
                timeoutMillis: Long
            ): String = """
                {
                  "engineVersion":"20",
                  "claimKey":"terminology:macroverso",
                  "value":"nonstandard term",
                  "displayText":"Macroverso es un término no estandarizado.",
                  "sourceIds":[],
                  "independentSourceCount":0,
                  "authoritative":false
                }
            """.trimIndent()
        }
        val provider = SupabaseUltraResearchProvider(
            supabaseUrl = "https://example.supabase.co",
            publishableKey = "sb_publishable_test",
            transport = transport,
            requiredEngineVersion = UltraResearchProtocol.ENGINE_VERSION
        )
        val request = UltraGeneralQueryRouter.classify("¿Qué es un macroverso?")
            .copy(verificationMode = UltraVerificationMode.OPTIONAL)
        val evidence = assertIs<UltraProviderResult.Evidence>(
            provider.fetchResult(request)
        ).evidence
        assertTrue(evidence.sourceId.isEmpty())
        assertEquals(0, evidence.independentSourceCount)
        assertFalse(evidence.authoritative)
    }

    @Test
    fun emptyCorroborationMustNotMakeAnUnsourcedAuthorityClaimValid() {
        val transport = object : UltraResearchBackendTransport {
            override fun post(
                endpoint: String,
                apiKey: String,
                body: String,
                timeoutMillis: Long
            ): String = """
                {
                  "engineVersion":"20",
                  "claimKey":"local-stable:planet",
                  "value":"planet",
                  "displayText":"Respuesta no verificada.",
                  "sourceIds":["","  "],
                  "independentSourceCount":2,
                  "authoritative":true
                }
            """.trimIndent()
        }
        val provider = SupabaseUltraResearchProvider(
            supabaseUrl = "https://example.supabase.co",
            publishableKey = "sb_publishable_test",
            transport = transport,
            requiredEngineVersion = UltraResearchProtocol.ENGINE_VERSION
        )
        val failure = assertIs<UltraProviderResult.Failure>(
            provider.fetchResult(UltraGeneralQueryRouter.classify("¿Qué es un planeta?"))
        )
        assertEquals("INVALID_BACKEND_RESPONSE", failure.reasonCode)
    }
}
