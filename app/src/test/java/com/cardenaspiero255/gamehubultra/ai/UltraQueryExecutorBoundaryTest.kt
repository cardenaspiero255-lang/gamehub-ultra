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
    fun optionalQueryWithoutStableFallbackUsesResearchBeforeGenericLocalChat() {
        val provider = object : UltraResearchProvider {
            override val id = "encyclopedia"

            override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence =
                UltraResearchEvidence(
                    claimKey = "motor",
                    value = "machine-energy-motion",
                    displayText = "Un motor transforma energía en movimiento o trabajo mecánico.",
                    sourceId = "encyclopedia",
                    authoritative = true
                )
        }
        val engine = UltraVerifiedResearchEngine(listOf(provider))
        val executor: UltraQueryExecutor = DefaultUltraQueryExecutor(
            coordinator = UltraQueryExecutionCoordinator(engine)
        )
        val route = UltraAgentRoute.Chat(
            message = "Ultra, ¿qué es un motor?",
            query = UltraGeneralQueryRouter.classify("Ultra, ¿qué es un motor?")
        )
        var genericLocalCalls = 0

        try {
            val answer = executor.answer(
                route = route,
                stableKnowledgeFallback = { null },
                localChat = {
                    genericLocalCalls += 1
                    "Soy Ultra. Puedo ayudarte con consultas generales."
                }
            )

            assertEquals(
                "Un motor transforma energía en movimiento o trabajo mecánico.",
                answer
            )
            assertEquals(0, genericLocalCalls)
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


    @Test
    fun stableKnowledgeNetworkFailureNeverLeaksGenericServiceUnavailableMessage() {
        val failingProvider = object : UltraResearchProvider {
            override val id = "offline-network"

            override fun fetch(
                request: UltraGeneralQueryRequest
            ): UltraResearchEvidence = error("network unavailable")

            override fun fetchResult(
                request: UltraGeneralQueryRequest
            ): UltraProviderResult =
                UltraProviderResult.Failure(
                    reasonCode = "BACKEND_NETWORK_FAILURE",
                    message = "network unavailable",
                    retryable = true,
                    stage = "client-network"
                )
        }
        val engine = UltraVerifiedResearchEngine(listOf(failingProvider))
        val executor: UltraQueryExecutor = DefaultUltraQueryExecutor(
            coordinator = UltraQueryExecutionCoordinator(engine)
        )
        val route = UltraAgentRoute.Chat(
            message = "Ultra, ¿qué es la fotosíntesis?",
            query = UltraGeneralQueryRouter.classify(
                "Ultra, ¿qué es la fotosíntesis?"
            )
        )

        try {
            val answer = executor.answer(
                route = route,
                stableKnowledgeFallback = { null },
                localChat = { "chat local genérico" }
            )

            kotlin.test.assertFalse(
                answer.contains(
                    "El servicio de consulta no está disponible ahora",
                    ignoreCase = true
                ),
                answer
            )
            kotlin.test.assertTrue(
                answer.contains("verificar", ignoreCase = true) ||
                    answer.contains("fiable", ignoreCase = true),
                answer
            )
        } finally {
            engine.close()
        }
    }

}
