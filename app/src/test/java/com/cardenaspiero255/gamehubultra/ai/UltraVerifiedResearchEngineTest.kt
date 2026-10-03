package com.cardenaspiero255.gamehubultra.ai

import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UltraVerifiedResearchEngineTest {
    @Test
    fun slowProviderIsCutOffByRequestTimeout() {
        val provider = object : UltraResearchProvider {
            override val id = "slow"
            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence {
                Thread.sleep(250)
                return evidence("weather", "sunny", "Soleado")
            }
        }
        val engine = UltraVerifiedResearchEngine(listOf(provider))
        val request = UltraGeneralQueryRouter.classify("Ultra, clima de hoy")
            .copy(timeoutMillis = 40L)

        val result = engine.answer(request)

        assertTrue(result.timedOut)
        assertTrue(result.abstained)
        assertTrue(result.message.contains("tardó demasiado", ignoreCase = true))
        engine.close()
    }

    @Test
    fun failedPrimaryProviderUsesHealthyFallback() {
        val primary = object : UltraResearchProvider {
            override val id = "primary"
            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence =
                error("provider down")
        }
        val fallback = object : UltraResearchProvider {
            override val id = "fallback"
            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence =
                evidence(
                    claimKey = "weather",
                    value = "sunny",
                    text = "Soleado, 22 °C",
                    authoritative = true
                )
        }
        val engine = UltraVerifiedResearchEngine(listOf(primary, fallback))

        val result = engine.answer(
            UltraGeneralQueryRouter.classify("Ultra, clima de hoy")
        )

        assertFalse(result.abstained)
        assertTrue(result.fallbackUsed)
        assertEquals(listOf("source"), result.sources)
        assertTrue(result.message.contains("22"))
        engine.close()
    }

    @Test
    fun contradictorySourcesLowerConfidenceAndAbstain() {
        val first = fixedProvider("one", "price", "100", "Precio: 100")
        val second = fixedProvider("two", "price", "140", "Precio: 140")
        val engine = UltraVerifiedResearchEngine(listOf(first, second))

        val result = engine.answer(
            UltraGeneralQueryRouter.classify("Ultra, precio actual del producto")
        )

        assertEquals(UltraAnswerConfidence.LOW, result.confidence)
        assertTrue(result.abstained)
        assertTrue(result.message.contains("corroboración", ignoreCase = true))
        engine.close()
    }

    @Test
    fun agreeingIndependentSourcesProduceHighConfidence() {
        val first = fixedProvider("one", "release", "2026-10-01", "Sale el 1 de octubre")
        val second = fixedProvider("two", "release", "2026-10-01", "Lanzamiento: 1 de octubre")
        val engine = UltraVerifiedResearchEngine(listOf(first, second))

        val result = engine.answer(
            UltraGeneralQueryRouter.classify("Ultra, cuál es la fecha actual de lanzamiento")
        )

        assertEquals(UltraAnswerConfidence.HIGH, result.confidence)
        assertFalse(result.abstained)
        assertEquals(2, result.sources.size)
        engine.close()
    }

    @Test
    fun jwtOrApiSecretIsNeverSentToProvider() {
        val calls = AtomicInteger(0)
        val provider = object : UltraResearchProvider {
            override val id = "should-not-run"
            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence {
                calls.incrementAndGet()
                return evidence("anything", "value", "answer")
            }
        }
        val engine = UltraVerifiedResearchEngine(listOf(provider))

        val result = engine.answer(
            UltraGeneralQueryRouter.classify(
                "Ultra, busca esto con token=eyJhbGciOiJIUzI1NiJ9.abcdefghi.signature123"
            )
        )

        assertEquals(0, calls.get())
        assertTrue(result.abstained)
        assertTrue(result.sensitiveInputBlocked)
        engine.close()
    }

    @Test
    fun freshCacheAvoidsCallingProviderTwiceAndExpiresByTtl() {
        var now = 1_000L
        val calls = AtomicInteger(0)
        val provider = object : UltraResearchProvider {
            override val id = "weather"
            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence {
                calls.incrementAndGet()
                return evidence(
                    claimKey = "weather",
                    value = "sunny",
                    text = "Soleado",
                    authoritative = true
                )
            }
        }
        val cache = UltraResearchCache()
        val engine = UltraVerifiedResearchEngine(
            providers = listOf(provider),
            cache = cache,
            nowMillis = { now }
        )
        val request = UltraGeneralQueryRouter.classify("Ultra, clima de hoy")

        val first = engine.answer(request)
        val second = engine.answer(request)
        now += UltraResearchCache.CURRENT_DATA_TTL_MS + 1
        val third = engine.answer(request)

        assertFalse(first.fromCache)
        assertTrue(second.fromCache)
        assertFalse(third.fromCache)
        assertEquals(2, calls.get())
        engine.close()
    }

    @Test
    fun lowConfidenceGeneralKnowledgeIsReturnedInsteadOfGenericAbstention() {
        val provider = fixedProvider(
            providerId = "general-assistant",
            claimKey = "general:sentimientos",
            value = "respuesta-general",
            text = "Los sentimientos son experiencias afectivas conscientes."
        )
        val engine = UltraVerifiedResearchEngine(listOf(provider))

        val result = engine.answer(
            UltraGeneralQueryRouter.classify("Ultra, qué son los sentimientos")
        )

        assertEquals(UltraAnswerConfidence.LOW, result.confidence)
        assertFalse(result.abstained)
        assertEquals(
            "Los sentimientos son experiencias afectivas conscientes.",
            result.message
        )
        engine.close()
    }

    @Test
    fun freshGeneralKnowledgeDoesNotReuseStableCacheEntry() {
        var now = 10_000L
        val calls = AtomicInteger(0)
        val provider = object : UltraResearchProvider {
            override val id = "general"

            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence {
                val call = calls.incrementAndGet()
                return evidence(
                    claimKey = "topic",
                    value = "value-$call",
                    text = "respuesta-$call",
                    authoritative = true
                )
            }
        }
        val engine = UltraVerifiedResearchEngine(
            providers = listOf(provider),
            cache = UltraResearchCache(),
            nowMillis = { now }
        )
        val stable = UltraGeneralQueryRequest(
            originalText = "mismo tema",
            kind = UltraGeneralQueryKind.GENERAL_KNOWLEDGE,
            requiresInternet = true,
            requiresFreshData = false,
            timeoutMillis = 5_000L
        )
        val fresh = stable.copy(requiresFreshData = true)

        try {
            val stableAnswer = engine.answer(stable)
            val freshAnswer = engine.answer(fresh)

            assertEquals("respuesta-1", stableAnswer.message)
            assertEquals("respuesta-2", freshAnswer.message)
            assertFalse(freshAnswer.fromCache)
            assertEquals(2, calls.get())
        } finally {
            engine.close()
        }
    }

    @Test
    fun currentDataKindUsesCurrentDataTtlEvenWhenFreshFlagIsFalse() {
        var now = 30_000L
        val calls = AtomicInteger(0)
        val provider = object : UltraResearchProvider {
            override val id = "current-data"

            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence {
                val call = calls.incrementAndGet()
                return evidence(
                    claimKey = "current",
                    value = "value-$call",
                    text = "actual-$call",
                    authoritative = true
                )
            }
        }
        val engine = UltraVerifiedResearchEngine(
            providers = listOf(provider),
            cache = UltraResearchCache(),
            nowMillis = { now }
        )
        val request = UltraGeneralQueryRequest(
            originalText = "dato actual con flags inconsistentes",
            kind = UltraGeneralQueryKind.CURRENT_DATA,
            requiresInternet = true,
            requiresFreshData = false,
            timeoutMillis = 5_000L
        )

        try {
            val first = engine.answer(request)
            val cached = engine.answer(request)
            now += UltraResearchCache.CURRENT_DATA_TTL_MS + 1
            val refreshed = engine.answer(request)

            assertFalse(first.fromCache)
            assertTrue(cached.fromCache)
            assertFalse(refreshed.fromCache)
            assertEquals("actual-2", refreshed.message)
            assertEquals(2, calls.get())
        } finally {
            engine.close()
        }
    }


    @Test
    fun freshGeneralKnowledgeCacheUsesCurrentDataTtl() {
        var now = 20_000L
        val calls = AtomicInteger(0)
        val provider = object : UltraResearchProvider {
            override val id = "general"

            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence {
                val call = calls.incrementAndGet()
                return evidence(
                    claimKey = "topic",
                    value = "fresh-$call",
                    text = "fresca-$call",
                    authoritative = true
                )
            }
        }
        val engine = UltraVerifiedResearchEngine(
            providers = listOf(provider),
            cache = UltraResearchCache(),
            nowMillis = { now }
        )
        val request = UltraGeneralQueryRequest(
            originalText = "tema con frescura",
            kind = UltraGeneralQueryKind.GENERAL_KNOWLEDGE,
            requiresInternet = true,
            requiresFreshData = true,
            timeoutMillis = 5_000L
        )

        try {
            val first = engine.answer(request)
            val cached = engine.answer(request)
            now += UltraResearchCache.CURRENT_DATA_TTL_MS + 1
            val refreshed = engine.answer(request)

            assertFalse(first.fromCache)
            assertTrue(cached.fromCache)
            assertFalse(refreshed.fromCache)
            assertEquals("fresca-2", refreshed.message)
            assertEquals(2, calls.get())
        } finally {
            engine.close()
        }
    }


    @Test
    fun optionalStableKnowledgeChoosesHigherQualityEvidenceWhenProvidersParaphrase() {
        val primary = object : UltraResearchProvider {
            override val id = "primary-multi-source"

            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence =
                UltraResearchEvidence(
                    claimKey = "general:motor",
                    value = "energia-a-trabajo-mecanico",
                    displayText = "Un motor transforma energía en trabajo mecánico o movimiento.",
                    sourceId = "https://fuente-uno.example/motor",
                    supportingSourceIds = listOf("https://fuente-dos.example/motor"),
                    independentSourceCount = 2,
                    authoritative = false
                )
        }
        val publicFallback = object : UltraResearchProvider {
            override val id = "wikimedia-public"

            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence =
                UltraResearchEvidence(
                    claimKey = "general:motor",
                    value = "maquina-que-produce-movimiento",
                    displayText = "Un motor es una máquina capaz de producir movimiento.",
                    sourceId = "https://es.wikipedia.org/wiki/Motor",
                    independentSourceCount = 1,
                    authoritative = true
                )
        }
        val engine = UltraVerifiedResearchEngine(
            providers = listOf(primary, publicFallback)
        )
        val request = UltraGeneralQueryRouter.classify("Ultra, ¿qué es un motor?")

        try {
            val result = engine.answer(request)

            assertFalse(result.abstained)
            assertEquals(
                "Un motor transforma energía en trabajo mecánico o movimiento.",
                result.message
            )
            assertEquals(UltraAnswerConfidence.HIGH, result.confidence)
            assertFalse(result.fallbackUsed)
        } finally {
            engine.close()
        }
    }


    @Test
    fun optionalStableKnowledgeAbstainsWhenProvidersGenuinelyConflict() {
        val primary = object : UltraResearchProvider {
            override val id = "primary-multi-source"

            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence =
                UltraResearchEvidence(
                    claimKey = "general:motor",
                    value = "maquina-que-transforma-energia",
                    displayText = "Un motor es una máquina que transforma energía en trabajo mecánico.",
                    sourceId = "https://fuente-uno.example/motor",
                    supportingSourceIds = listOf("https://fuente-dos.example/motor"),
                    independentSourceCount = 2,
                    authoritative = false
                )
        }
        val contradictoryFallback = object : UltraResearchProvider {
            override val id = "wikimedia-public"

            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence =
                UltraResearchEvidence(
                    claimKey = "general:motor",
                    value = "animal-domestico",
                    displayText = "Un motor es un animal doméstico de cuatro patas.",
                    sourceId = "https://es.wikipedia.org/wiki/Motor",
                    independentSourceCount = 1,
                    authoritative = true
                )
        }
        val engine = UltraVerifiedResearchEngine(
            providers = listOf(primary, contradictoryFallback)
        )

        try {
            val result = engine.answer(
                UltraGeneralQueryRouter.classify("Ultra, ¿qué es un motor?")
            )

            assertTrue(result.abstained)
            assertEquals(UltraAnswerConfidence.LOW, result.confidence)
            assertTrue(
                result.message.contains("corroboración", ignoreCase = true)
            )
        } finally {
            engine.close()
        }
    }

    @Test
    fun optionalStableKnowledgeRejectsConflictingNumericFacts() {
        val twoMoons = object : UltraResearchProvider {
            override val id = "primary"

            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence =
                UltraResearchEvidence(
                    claimKey = "general:marte",
                    value = "marte tiene 2 lunas",
                    displayText = "Marte tiene 2 lunas.",
                    sourceId = "https://source-a.example/marte",
                    independentSourceCount = 2,
                    authoritative = true
                )
        }
        val threeMoons = object : UltraResearchProvider {
            override val id = "fallback"

            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence =
                UltraResearchEvidence(
                    claimKey = "general:marte",
                    value = "marte tiene 3 lunas",
                    displayText = "Marte tiene 3 lunas.",
                    sourceId = "https://source-b.example/marte",
                    independentSourceCount = 1,
                    authoritative = true
                )
        }
        val engine = UltraVerifiedResearchEngine(listOf(twoMoons, threeMoons))

        try {
            val result = engine.answer(
                UltraGeneralQueryRouter.classify("Ultra, ¿cuántas lunas tiene Marte?")
            )

            assertTrue(result.abstained)
            assertEquals("INSUFFICIENT_CORROBORATION", result.reasonCode)
            assertEquals(UltraAnswerConfidence.LOW, result.confidence)
        } finally {
            engine.close()
        }
    }

    @Test
    fun optionalStableKnowledgeRejectsConflictingWrittenQuantities() {
        val twoMoons = object : UltraResearchProvider {
            override val id = "primary-written"

            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence =
                UltraResearchEvidence(
                    claimKey = "general:marte",
                    value = "marte tiene dos lunas",
                    displayText = "Marte tiene dos lunas.",
                    sourceId = "https://source-a.example/marte",
                    independentSourceCount = 2,
                    authoritative = true
                )
        }
        val threeMoons = object : UltraResearchProvider {
            override val id = "fallback-written"

            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence =
                UltraResearchEvidence(
                    claimKey = "general:marte",
                    value = "marte tiene tres lunas",
                    displayText = "Marte tiene tres lunas.",
                    sourceId = "https://source-b.example/marte",
                    independentSourceCount = 1,
                    authoritative = true
                )
        }
        val engine = UltraVerifiedResearchEngine(listOf(twoMoons, threeMoons))

        try {
            val result = engine.answer(
                UltraGeneralQueryRouter.classify("Ultra, ¿cuántas lunas tiene Marte?")
            )

            assertTrue(result.abstained)
            assertEquals("INSUFFICIENT_CORROBORATION", result.reasonCode)
            assertEquals(UltraAnswerConfidence.LOW, result.confidence)
        } finally {
            engine.close()
        }
    }

    @Test
    fun optionalStableKnowledgeAllowsDifferentIncidentalDates() {
        val first = object : UltraResearchProvider {
            override val id = "first"

            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence =
                UltraResearchEvidence(
                    claimKey = "general:marte",
                    value = "marte es un planeta rocoso del sistema solar",
                    displayText =
                        "Marte es un planeta rocoso del sistema solar. Fuente actualizada el 12 de agosto.",
                    sourceId = "https://source-a.example/marte",
                    independentSourceCount = 2,
                    authoritative = true
                )
        }
        val second = object : UltraResearchProvider {
            override val id = "second"

            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence =
                UltraResearchEvidence(
                    claimKey = "general:marte",
                    value = "marte es un planeta rocoso del sistema solar",
                    displayText =
                        "Marte es un planeta rocoso del sistema solar. Fuente revisada el 18 de agosto.",
                    sourceId = "https://source-b.example/marte",
                    independentSourceCount = 1,
                    authoritative = true
                )
        }
        val engine = UltraVerifiedResearchEngine(listOf(first, second))

        try {
            val result = engine.answer(
                UltraGeneralQueryRouter.classify("Ultra, ¿qué es Marte?")
            )

            assertFalse(result.abstained)
            assertEquals(UltraAnswerConfidence.HIGH, result.confidence)
            assertTrue(result.message.contains("Marte es un planeta rocoso"))
        } finally {
            engine.close()
        }
    }

    @Test
    fun optionalStableKnowledgeRejectsNegatedContradictionDespiteTokenOverlap() {
        val positive = object : UltraResearchProvider {
            override val id = "positive"

            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence =
                UltraResearchEvidence(
                    claimKey = "general:fotosintesis",
                    value = "la fotosintesis produce oxigeno",
                    displayText = "La fotosíntesis produce oxígeno durante el proceso.",
                    sourceId = "https://source-a.example/fotosintesis",
                    independentSourceCount = 2,
                    authoritative = true
                )
        }
        val negative = object : UltraResearchProvider {
            override val id = "negative"

            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence =
                UltraResearchEvidence(
                    claimKey = "general:fotosintesis",
                    value = "la fotosintesis no produce oxigeno",
                    displayText = "La fotosíntesis no produce oxígeno durante el proceso.",
                    sourceId = "https://source-b.example/fotosintesis",
                    independentSourceCount = 1,
                    authoritative = true
                )
        }
        val engine = UltraVerifiedResearchEngine(listOf(positive, negative))

        try {
            val result = engine.answer(
                UltraGeneralQueryRouter.classify("Ultra, ¿qué es la fotosíntesis?")
            )

            assertTrue(result.abstained)
            assertEquals("INSUFFICIENT_CORROBORATION", result.reasonCode)
            assertEquals(UltraAnswerConfidence.LOW, result.confidence)
        } finally {
            engine.close()
        }
    }

    @Test
    fun optionalStableKnowledgeAllowsNegationOnDifferentProposition() {
        val concise = object : UltraResearchProvider {
            override val id = "concise"

            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence =
                UltraResearchEvidence(
                    claimKey = "general:motor",
                    value = "un motor transforma energia",
                    displayText = "Un motor transforma energía en movimiento.",
                    sourceId = "https://source-a.example/motor",
                    independentSourceCount = 2,
                    authoritative = true
                )
        }
        val explanatory = object : UltraResearchProvider {
            override val id = "explanatory"

            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence =
                UltraResearchEvidence(
                    claimKey = "general:motor",
                    value = "un motor no crea energia la transforma",
                    displayText = "Un motor no crea energía; la transforma en movimiento.",
                    sourceId = "https://source-b.example/motor",
                    independentSourceCount = 1,
                    authoritative = true
                )
        }
        val engine = UltraVerifiedResearchEngine(listOf(concise, explanatory))

        try {
            val result = engine.answer(
                UltraGeneralQueryRouter.classify("Ultra, ¿qué es un motor?")
            )

            assertFalse(result.abstained)
            assertEquals(UltraAnswerConfidence.HIGH, result.confidence)
        } finally {
            engine.close()
        }
    }

    @Test
    fun fastFallbackStillAllowsHealthyPrimaryToFinish() {
        val primary = object : UltraResearchProvider {
            override val id = "supabase-ultra-research"

            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence {
                Thread.sleep(600L)
                return UltraResearchEvidence(
                    claimKey = "general:motor",
                    value = "primary-verified",
                    displayText = "Un motor transforma energía en trabajo mecánico.",
                    sourceId = "https://primary.example/motor",
                    supportingSourceIds = listOf("https://primary-two.example/motor"),
                    independentSourceCount = 2,
                    authoritative = true
                )
            }
        }
        val fastFallback = object : UltraResearchProvider {
            override val id = "wikimedia-public"

            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence =
                UltraResearchEvidence(
                    claimKey = "general:motor",
                    value = "public-fallback",
                    displayText = "Un motor es una máquina que produce movimiento.",
                    sourceId = "https://es.wikipedia.org/wiki/Motor",
                    independentSourceCount = 1,
                    authoritative = true
                )
        }
        val engine = UltraVerifiedResearchEngine(listOf(primary, fastFallback))
        val request = UltraGeneralQueryRouter
            .classify("Ultra, ¿qué es un motor?")
            .copy(timeoutMillis = 2_000L)

        try {
            val result = engine.answer(request)

            assertFalse(result.abstained)
            assertFalse(result.fallbackUsed)
            assertEquals(
                "Un motor transforma energía en trabajo mecánico.",
                result.message
            )
            assertEquals(UltraAnswerConfidence.HIGH, result.confidence)
        } finally {
            engine.close()
        }
    }

    @Test
    fun authoritativePublicFallbackDoesNotWaitForStalledPrimaryUntilFullDeadline() {
        val primaryInterrupted = java.util.concurrent.atomic.AtomicBoolean(false)
        val primary = object : UltraResearchProvider {
            override val id = "supabase-ultra-research"

            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence {
                try {
                    Thread.sleep(5_000L)
                } catch (interrupted: InterruptedException) {
                    primaryInterrupted.set(true)
                    throw interrupted
                }
                return UltraResearchEvidence(
                    claimKey = "general:motor",
                    value = "primary",
                    displayText = "Respuesta primaria tardía.",
                    sourceId = "https://primary.example/motor",
                    independentSourceCount = 2
                )
            }
        }
        val publicFallback = object : UltraResearchProvider {
            override val id = "wikimedia-public"

            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence =
                UltraResearchEvidence(
                    claimKey = "general:motor",
                    value = "maquina-que-transforma-energia",
                    displayText = "Un motor es una máquina que transforma energía en movimiento.",
                    sourceId = "https://es.wikipedia.org/wiki/Motor",
                    independentSourceCount = 1,
                    authoritative = true
                )
        }
        val engine = UltraVerifiedResearchEngine(
            providers = listOf(primary, publicFallback)
        )
        val request = UltraGeneralQueryRouter
            .classify("Ultra, ¿qué es un motor?")
            .copy(timeoutMillis = 2_000L)
        val started = System.nanoTime()

        try {
            val result = engine.answer(request)
            val elapsedMillis = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - started
            )

            assertFalse(result.abstained)
            assertTrue(result.fallbackUsed)
            assertTrue(
                elapsedMillis < 1_200L,
                "El fallback tardó ${elapsedMillis}ms; no debe esperar el deadline completo."
            )
            assertEquals(UltraAnswerConfidence.MEDIUM, result.confidence)
            Thread.sleep(50L)
            assertTrue(primaryInterrupted.get())
        } finally {
            engine.close()
        }
    }


    @Test
    fun graceCancellationExplicitlyReleasesBlockingProvider() {
        val entered = java.util.concurrent.CountDownLatch(1)
        val released = java.util.concurrent.CountDownLatch(1)
        val cancelCalls = AtomicInteger(0)
        val primary = object : UltraResearchProvider {
            override val id = "blocking-primary"

            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence {
                entered.countDown()
                while (released.count > 0L) {
                    try {
                        released.await()
                    } catch (_: InterruptedException) {
                        // Simula I/O bloqueante que no se libera solo con interrupt().
                    }
                }
                error("La llamada primaria cancelada no debe producir evidencia")
            }

            override fun cancelActiveRequest(worker: Thread) {
                cancelCalls.incrementAndGet()
                released.countDown()
            }
        }
        val fallback = object : UltraResearchProvider {
            override val id = "wikimedia-public"

            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence =
                UltraResearchEvidence(
                    claimKey = "general:motor",
                    value = "motor transforma energia",
                    displayText = "Un motor transforma energía en movimiento.",
                    sourceId = "https://es.wikipedia.org/wiki/Motor",
                    authoritative = true
                )
        }
        val engine = UltraVerifiedResearchEngine(listOf(primary, fallback))

        try {
            val result = engine.answer(
                UltraGeneralQueryRouter
                    .classify("Ultra, ¿qué es un motor?")
                    .copy(timeoutMillis = 2_000L)
            )

            assertFalse(result.abstained)
            assertTrue(result.fallbackUsed)
            assertEquals(0L, entered.count)
            assertEquals(1, cancelCalls.get())
            assertTrue(released.await(1, java.util.concurrent.TimeUnit.SECONDS))
        } finally {
            released.countDown()
            engine.close()
        }
    }

    @Test
    fun nonApplicablePublicFallbackDoesNotHidePrimaryFailure() {
        val primary = object : UltraResearchProvider {
            override val id = "supabase-ultra-research"

            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence =
                error("unused")

            override fun fetchResult(
                request: UltraGeneralQueryRequest
            ): UltraProviderResult =
                UltraProviderResult.Failure(
                    reasonCode = "BACKEND_NETWORK_FAILURE",
                    message = "backend offline",
                    retryable = true,
                    stage = "supabase"
                )
        }
        val fallback = WikimediaUltraResearchProvider(
            UltraPublicKnowledgeTransport { _, _ ->
                error("CURRENT_DATA no debe consultar Wikimedia")
            }
        )
        val engine = UltraVerifiedResearchEngine(listOf(primary, fallback))

        try {
            val result = engine.answer(
                UltraGeneralQueryRouter.classify(
                    "Ultra, ¿cuál es el clima actual en Rancagua?"
                )
            )

            assertTrue(result.abstained)
            assertEquals("BACKEND_NETWORK_FAILURE", result.reasonCode)
            assertTrue(result.retryable)
            assertEquals("supabase", result.stage)
            assertTrue(
                result.message.contains("no está disponible", ignoreCase = true)
            )
        } finally {
            engine.close()
        }
    }

    @Test
    fun timeoutIsNotHiddenByNonApplicablePublicFallback() {
        val primary = object : UltraResearchProvider {
            override val id = "supabase-ultra-research"

            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence {
                Thread.sleep(5_000L)
                error("La primaria bloqueada no debe completar")
            }
        }
        val fallback = WikimediaUltraResearchProvider(
            UltraPublicKnowledgeTransport { _, _ ->
                error("CURRENT_DATA no debe consultar Wikimedia")
            }
        )
        val engine = UltraVerifiedResearchEngine(listOf(primary, fallback))

        try {
            val result = engine.answer(
                UltraGeneralQueryRouter.classify(
                    "Ultra, ¿cuál es el clima actual en Rancagua?"
                ).copy(timeoutMillis = 60L)
            )

            assertTrue(result.abstained)
            assertTrue(result.timedOut)
            assertEquals("UPSTREAM_TIMEOUT", result.reasonCode)
            assertTrue(result.retryable)
            assertTrue(
                result.message.contains("tardó demasiado", ignoreCase = true)
            )
        } finally {
            engine.close()
        }
    }

    @Test
    fun irrelevantWikimediaAbstentionDoesNotHidePrimaryFailure() {
        val primary = object : UltraResearchProvider {
            override val id = "supabase-ultra-research"

            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence =
                error("unused")

            override fun fetchResult(
                request: UltraGeneralQueryRequest
            ): UltraProviderResult =
                UltraProviderResult.Failure(
                    reasonCode = "BACKEND_NETWORK_FAILURE",
                    message = "backend offline",
                    retryable = true,
                    stage = "supabase"
                )
        }
        val fallback = WikimediaUltraResearchProvider(
            UltraPublicKnowledgeTransport { url, _ ->
                if (url.contains("list=search")) {
                    UltraResearchHttpResponse(
                        200,
                        """{"query":{"search":[{"title":"Motorola"}]}}"""
                    )
                } else {
                    error("No debe consultar el extracto de un resultado irrelevante")
                }
            }
        )
        val engine = UltraVerifiedResearchEngine(listOf(primary, fallback))

        try {
            val result = engine.answer(
                UltraGeneralQueryRouter.classify(
                    "Ultra, ¿qué es un motor?"
                )
            )

            assertTrue(result.abstained)
            assertEquals("BACKEND_NETWORK_FAILURE", result.reasonCode)
            assertTrue(result.retryable)
            assertEquals("supabase", result.stage)
            assertTrue(
                result.message.contains("no está disponible", ignoreCase = true)
            )
        } finally {
            engine.close()
        }
    }

    @Test
    fun copulaNegationDoesNotCreateFalseConflictForCompatibleFacts() {
        val first = object : UltraResearchProvider {
            override val id = "one"

            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence =
                UltraResearchEvidence(
                    claimKey = "general:ballenas",
                    value = "las ballenas no son peces son mamiferos",
                    displayText = "Las ballenas no son peces, son mamíferos.",
                    sourceId = "https://source-a.example/ballenas",
                    independentSourceCount = 2,
                    authoritative = true
                )
        }
        val second = object : UltraResearchProvider {
            override val id = "two"

            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence =
                UltraResearchEvidence(
                    claimKey = "general:ballenas",
                    value = "las ballenas son mamiferos marinos",
                    displayText = "Las ballenas son mamíferos marinos.",
                    sourceId = "https://source-b.example/ballenas",
                    authoritative = true
                )
        }
        val engine = UltraVerifiedResearchEngine(listOf(first, second))

        try {
            val result = engine.answer(
                UltraGeneralQueryRouter.classify("Ultra, ¿qué son las ballenas?")
            )

            assertFalse(result.abstained)
            assertEquals(UltraAnswerConfidence.HIGH, result.confidence)
        } finally {
            engine.close()
        }
    }

    @Test
    fun noSoloSinoDoesNotCreateFalseConflict() {
        val expanded = object : UltraResearchProvider {
            override val id = "expanded"

            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence =
                UltraResearchEvidence(
                    claimKey = "general:fotosintesis",
                    value = "la fotosintesis no solo produce oxigeno sino tambien glucosa",
                    displayText =
                        "La fotosíntesis no solo produce oxígeno, sino también glucosa.",
                    sourceId = "https://source-a.example/fotosintesis",
                    authoritative = true
                )
        }
        val concise = object : UltraResearchProvider {
            override val id = "concise"

            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence =
                UltraResearchEvidence(
                    claimKey = "general:fotosintesis",
                    value = "la fotosintesis produce oxigeno y glucosa",
                    displayText =
                        "La fotosíntesis produce oxígeno y glucosa.",
                    sourceId = "https://source-b.example/fotosintesis",
                    authoritative = true
                )
        }
        val engine = UltraVerifiedResearchEngine(listOf(expanded, concise))

        try {
            val result = engine.answer(
                UltraGeneralQueryRouter.classify(
                    "Ultra, ¿qué es la fotosíntesis?"
                )
            )

            assertFalse(result.abstained)
            assertEquals(UltraAnswerConfidence.MEDIUM, result.confidence)
        } finally {
            engine.close()
        }
    }

    @Test
    fun simultaneousRequestsDoNotStarveHealthyFallbacks() {
        val firstFallbackCompleted = java.util.concurrent.CountDownLatch(1)
        val primary = object : UltraResearchProvider {
            override val id = "stalled-primary"

            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence {
                Thread.sleep(5_000L)
                error("La primaria bloqueada no debe ganar")
            }
        }
        val fallback = object : UltraResearchProvider {
            override val id = "wikimedia-public"

            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence {
                if (request.originalText.contains("primera", ignoreCase = true)) {
                    firstFallbackCompleted.countDown()
                }
                return UltraResearchEvidence(
                    claimKey = "general:motor",
                    value = "motor transforma energia",
                    displayText = "Un motor transforma energía en movimiento.",
                    sourceId = "https://es.wikipedia.org/wiki/Motor",
                    authoritative = true
                )
            }
        }
        val engine = UltraVerifiedResearchEngine(listOf(primary, fallback))
        val callers = java.util.concurrent.Executors.newFixedThreadPool(2)

        try {
            val first = callers.submit<UltraVerifiedResearchResult> {
                engine.answer(
                    UltraGeneralQueryRouter
                        .classify("Ultra, ¿qué es un motor? primera")
                        .copy(timeoutMillis = 2_000L)
                )
            }
            assertTrue(
                firstFallbackCompleted.await(
                    1,
                    java.util.concurrent.TimeUnit.SECONDS
                )
            )

            val started = System.nanoTime()
            val second = callers.submit<UltraVerifiedResearchResult> {
                engine.answer(
                    UltraGeneralQueryRouter
                        .classify("Ultra, ¿qué es un motor? segunda")
                        .copy(timeoutMillis = 2_000L)
                )
            }
            val secondResult = second.get(
                2,
                java.util.concurrent.TimeUnit.SECONDS
            )
            val elapsedMillis =
                java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
                    System.nanoTime() - started
                )

            assertFalse(secondResult.abstained)
            assertTrue(secondResult.fallbackUsed)
            assertTrue(
                elapsedMillis < 600L,
                "La segunda consulta tardó ${elapsedMillis}ms; el fallback fue bloqueado por otra consulta."
            )
            assertFalse(
                first.get(2, java.util.concurrent.TimeUnit.SECONDS).abstained
            )
        } finally {
            callers.shutdownNow()
            engine.close()
        }
    }

    private fun fixedProvider(
        providerId: String,
        claimKey: String,
        value: String,
        text: String
    ): UltraResearchProvider =
        object : UltraResearchProvider {
            override val id = providerId
            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence =
                evidence(claimKey, value, text).copy(sourceId = providerId)
        }

    private fun evidence(
        claimKey: String,
        value: String,
        text: String,
        authoritative: Boolean = false
    ) = UltraResearchEvidence(
        claimKey = claimKey,
        value = value,
        displayText = text,
        sourceId = "source",
        authoritative = authoritative
    )
}
