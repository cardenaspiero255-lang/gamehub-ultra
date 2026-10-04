package com.cardenaspiero255.gamehubultra.ai

import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UltraPersistentResearchCacheTest {

    @Test
    fun stableKnowledgeSurvivesEngineRecreationWithoutProviderCall() {
        var now = 1_000L
        val persistent = RecordingPersistentStore()
        val firstCalls = AtomicInteger(0)
        val request = UltraGeneralQueryRouter.classify("Ultra, ¿qué es un motor?")

        val firstCache = UltraResearchCache().also {
            it.attachPersistentStore(persistent)
        }
        val firstEngine = UltraVerifiedResearchEngine(
            providers = listOf(
                fixedProvider(firstCalls, "Un motor transforma energía en movimiento.")
            ),
            cache = firstCache,
            nowMillis = { now }
        )

        val first = firstEngine.answer(request)
        firstEngine.close()

        val secondCalls = AtomicInteger(0)
        val secondCache = UltraResearchCache().also {
            it.attachPersistentStore(persistent)
        }
        val secondEngine = UltraVerifiedResearchEngine(
            providers = listOf(
                fixedProvider(secondCalls, "respuesta que no debería usarse")
            ),
            cache = secondCache,
            nowMillis = { now }
        )

        val second = secondEngine.answer(request)
        secondEngine.close()

        assertFalse(first.fromCache)
        assertTrue(second.fromCache)
        assertEquals(first.message, second.message)
        assertEquals(1, firstCalls.get())
        assertEquals(0, secondCalls.get())
        assertEquals(1, persistent.writeCount)
    }

    @Test
    fun expiredStableKnowledgeIsRemovedAndRefetched() {
        var now = 10_000L
        val persistent = RecordingPersistentStore()
        val calls = AtomicInteger(0)
        val request = UltraGeneralQueryRouter.classify("Ultra, ¿qué es la fotosíntesis?")

        fun engine(): UltraVerifiedResearchEngine {
            val cache = UltraResearchCache().also {
                it.attachPersistentStore(persistent)
            }
            return UltraVerifiedResearchEngine(
                providers = listOf(
                    object : UltraResearchProvider {
                        override val id = "encyclopedia"
                        override fun fetch(
                            request: UltraGeneralQueryRequest
                        ): UltraResearchEvidence {
                            val call = calls.incrementAndGet()
                            return UltraResearchEvidence(
                                claimKey = "photosynthesis",
                                value = "definition-$call",
                                displayText = "Fotosíntesis respuesta $call",
                                sourceId = "encyclopedia",
                                authoritative = true
                            )
                        }
                    }
                ),
                cache = cache,
                nowMillis = { now }
            )
        }

        val firstEngine = engine()
        val first = firstEngine.answer(request)
        firstEngine.close()

        now += UltraResearchCache.GENERAL_KNOWLEDGE_TTL_MS + 1

        val secondEngine = engine()
        val second = secondEngine.answer(request)
        secondEngine.close()

        assertEquals("Fotosíntesis respuesta 1", first.message)
        assertEquals("Fotosíntesis respuesta 2", second.message)
        assertFalse(second.fromCache)
        assertEquals(2, calls.get())
        assertTrue(persistent.removeCount >= 1)
    }

    @Test
    fun currentDataNeverWritesPersistentKnowledgeCache() {
        val persistent = RecordingPersistentStore()
        val calls = AtomicInteger(0)
        val cache = UltraResearchCache().also {
            it.attachPersistentStore(persistent)
        }
        val engine = UltraVerifiedResearchEngine(
            providers = listOf(
                fixedProvider(calls, "22 °C y despejado")
            ),
            cache = cache
        )

        val result = engine.answer(
            UltraGeneralQueryRouter.classify("Ultra, clima de hoy en Santiago")
        )
        engine.close()

        assertFalse(result.abstained)
        assertEquals(1, calls.get())
        assertEquals(0, persistent.writeCount)
        assertTrue(persistent.entries.isEmpty())
    }

    @Test
    fun lowConfidenceResultCannotBePersistedByCacheDirectly() {
        val persistent = RecordingPersistentStore()
        val cache = UltraResearchCache().also {
            it.attachPersistentStore(persistent)
        }

        cache.put(
            key = "low-confidence",
            result = UltraVerifiedResearchResult(
                message = "Respuesta no corroborada",
                confidence = UltraAnswerConfidence.LOW,
                abstained = false
            ),
            expiresAtMillis = Long.MAX_VALUE,
            persist = true
        )

        assertEquals(0, persistent.writeCount)
        assertTrue(persistent.entries.isEmpty())
    }

    private fun fixedProvider(
        calls: AtomicInteger,
        answer: String
    ): UltraResearchProvider =
        object : UltraResearchProvider {
            override val id = "encyclopedia"

            override fun fetch(
                request: UltraGeneralQueryRequest
            ): UltraResearchEvidence {
                calls.incrementAndGet()
                return UltraResearchEvidence(
                    claimKey = "stable",
                    value = answer.lowercase(),
                    displayText = answer,
                    sourceId = "encyclopedia",
                    authoritative = true
                )
            }
        }

    private class RecordingPersistentStore : UltraResearchPersistentStore {
        val entries = mutableMapOf<String, UltraResearchPersistentEntry>()
        var writeCount = 0
        var removeCount = 0

        override fun read(key: String): UltraResearchPersistentEntry? =
            entries[key]

        override fun write(
            key: String,
            entry: UltraResearchPersistentEntry
        ) {
            writeCount += 1
            entries[key] = entry
        }

        override fun remove(key: String) {
            removeCount += 1
            entries.remove(key)
        }
    }
}
