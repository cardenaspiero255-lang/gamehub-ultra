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
}
