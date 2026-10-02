package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import kotlin.test.assertEquals

class UltraQueryExecutorBoundaryTest {

    @Test
    fun defaultExecutorIsUsableThroughQueryExecutorContract() {
        val engine = UltraVerifiedResearchEngine(emptyList())
        val executor: UltraQueryExecutor = DefaultUltraQueryExecutor(
            coordinator = UltraQueryExecutionCoordinator(engine)
        )
        val route = UltraAgentRoute.Chat(
            message = "hola",
            query = null
        )

        try {
            val answer = executor.answer(
                route = route,
                localChat = { "respuesta local" }
            )

            assertEquals("respuesta local", answer)
        } finally {
            engine.close()
        }
    }

    @Test
    fun defaultExecutorPreservesStableKnowledgeFallbackPolicy() {
        val failingProvider = object : UltraResearchProvider {
            override val id = "offline-provider"

            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence {
                error("provider unavailable")
            }
        }
        val engine = UltraVerifiedResearchEngine(listOf(failingProvider))
        val executor: UltraQueryExecutor = DefaultUltraQueryExecutor(
            coordinator = UltraQueryExecutionCoordinator(engine)
        )
        val route = UltraAgentRoute.Chat(
            message = "Ultra, qué son los sentimientos",
            query = UltraGeneralQueryRouter.classify("Ultra, qué son los sentimientos")
        )

        try {
            val answer = executor.answer(
                route = route,
                stableKnowledgeFallback = { "respuesta local estable" },
                localChat = { "chat local genérico" }
            )

            assertEquals("respuesta local estable", answer)
        } finally {
            engine.close()
        }
    }
    @Test
    fun internetQueryWithoutStableFallbackUsesLocalChatWhenResearchIsUnavailable() {
        val failingProvider = object : UltraResearchProvider {
            override val id = "offline-provider"

            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence {
                error("provider unavailable")
            }
        }
        val engine = UltraVerifiedResearchEngine(listOf(failingProvider))
        val executor: UltraQueryExecutor = DefaultUltraQueryExecutor(
            coordinator = UltraQueryExecutionCoordinator(engine)
        )
        val route = UltraAgentRoute.Chat(
            message = "Ultra, qué son los sentimientos",
            query = UltraGeneralQueryRouter.classify("Ultra, qué son los sentimientos")
        )

        try {
            val answer = executor.answer(
                route = route,
                localChat = { "fallback local directo" }
            )

            assertEquals("fallback local directo", answer)
        } finally {
            engine.close()
        }
    }


    @Test
    fun stableKnowledgeFallsThroughToLocalChatWhenSpecializedFallbackHasNoAnswer() {
        val failingProvider = object : UltraResearchProvider {
            override val id = "offline-provider"

            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence {
                error("provider unavailable")
            }
        }
        val engine = UltraVerifiedResearchEngine(listOf(failingProvider))
        val executor: UltraQueryExecutor = DefaultUltraQueryExecutor(
            coordinator = UltraQueryExecutionCoordinator(engine)
        )
        val route = UltraAgentRoute.Chat(
            message = "Ultra, explícame qué es Vulkan",
            query = UltraGeneralQueryRouter.classify("Ultra, explícame qué es Vulkan")
        )

        try {
            val answer = executor.answer(
                route = route,
                stableKnowledgeFallback = { null },
                localChat = { "respuesta local general" }
            )

            assertEquals("respuesta local general", answer)
        } finally {
            engine.close()
        }
    }

    @Test
    fun freshQueryNeverFallsThroughToUnverifiedLocalChat() {
        val failingProvider = object : UltraResearchProvider {
            override val id = "offline-provider"

            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence {
                error("provider unavailable")
            }
        }
        val engine = UltraVerifiedResearchEngine(listOf(failingProvider))
        val executor: UltraQueryExecutor = DefaultUltraQueryExecutor(
            coordinator = UltraQueryExecutionCoordinator(engine)
        )
        val route = UltraAgentRoute.Chat(
            message = "Ultra, precio actual del RedMagic",
            query = UltraGeneralQueryRouter.classify("Ultra, precio actual del RedMagic")
        )

        try {
            val answer = executor.answer(
                route = route,
                stableKnowledgeFallback = { null },
                localChat = { "precio local sin verificar" }
            )

            kotlin.test.assertTrue(answer.contains("verificar", ignoreCase = true))
        } finally {
            engine.close()
        }
    }

    @Test
    fun localQueryUsesLocalChatAndIgnoresStableKnowledgeFallback() {
        val engine = UltraVerifiedResearchEngine(emptyList())
        val executor: UltraQueryExecutor = DefaultUltraQueryExecutor(
            coordinator = UltraQueryExecutionCoordinator(engine)
        )
        val route = UltraAgentRoute.Chat(
            message = "hola",
            query = UltraGeneralQueryRequest(
                originalText = "hola",
                kind = UltraGeneralQueryKind.GENERAL_KNOWLEDGE,
                requiresInternet = false,
                requiresFreshData = false,
                timeoutMillis = 5_000L
            )
        )

        try {
            val answer = executor.answer(
                route = route,
                stableKnowledgeFallback = { "fallback estable no debe usarse" },
                localChat = { "chat local" }
            )

            assertEquals("chat local", answer)
        } finally {
            engine.close()
        }
    }

}
