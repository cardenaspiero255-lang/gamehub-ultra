package com.cardenaspiero255.gamehubultra

import com.cardenaspiero255.gamehubultra.ai.UltraMemoryScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UltraAssistantSessionControllerTest {

    @Test
    fun switchingGamesCancelsStaleConversationLoad() = runBlocking {
        val ownerScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val gameAStarted = CompletableDeferred<Unit>()
        val releaseGameA = CompletableDeferred<Unit>()

        val memory = object : UltraAssistantSessionMemory {
            override suspend fun warmUp() = Unit

            override suspend fun recentConversationLines(
                limit: Int,
                scope: UltraMemoryScope
            ): List<String> =
                when (scope.gamePackage) {
                    "game.a" -> {
                        gameAStarted.complete(Unit)
                        releaseGameA.await()
                        listOf("Ultra: conversación A")
                    }
                    "game.b" -> listOf("Ultra: conversación B")
                    else -> emptyList()
                }

            override fun enqueueSyncConversation(
                previous: List<String>,
                next: List<String>,
                scope: UltraMemoryScope,
                timestampMillis: Long
            ) = Unit

            override fun enqueueClearConversationHistory(scope: UltraMemoryScope) = Unit
        }

        try {
            val controller = UltraAssistantSessionController(
                ownerScope = ownerScope,
                memory = memory,
                maxHistory = 8,
                ioDispatcher = Dispatchers.Unconfined,
                publicationDispatcher = Dispatchers.Unconfined
            )

            val firstLoad = controller.selectGame("game.a")
            gameAStarted.await()

            val secondLoad = controller.selectGame("game.b")
            secondLoad.join()
            releaseGameA.complete(Unit)
            firstLoad.join()

            assertEquals(
                listOf("Ultra: conversación B"),
                controller.conversation.value
            )
            assertEquals("game.b", controller.selectedGamePackage.value)
        } finally {
            ownerScope.cancel()
        }
    }

    @Test
    fun conversationPersistenceUsesCurrentGameScope() = runBlocking {
        val ownerScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var persistedScope: UltraMemoryScope? = null
        var persistedNext: List<String> = emptyList()
        var persistedTimestamp = -1L

        val memory = object : UltraAssistantSessionMemory {
            override suspend fun warmUp() = Unit

            override suspend fun recentConversationLines(
                limit: Int,
                scope: UltraMemoryScope
            ): List<String> = emptyList()

            override fun enqueueSyncConversation(
                previous: List<String>,
                next: List<String>,
                scope: UltraMemoryScope,
                timestampMillis: Long
            ) {
                persistedScope = scope
                persistedNext = next
                persistedTimestamp = timestampMillis
            }

            override fun enqueueClearConversationHistory(scope: UltraMemoryScope) = Unit
        }

        try {
            val controller = UltraAssistantSessionController(
                ownerScope = ownerScope,
                memory = memory,
                maxHistory = 8,
                ioDispatcher = Dispatchers.Unconfined,
                publicationDispatcher = Dispatchers.Unconfined,
                nowMillis = { 1234L }
            )
            controller.selectGame("game.a").join()

            val next = listOf(
                "Tú: hola",
                "Ultra: Hola. ¿En qué te ayudo?"
            )
            controller.updateConversation(next)

            assertEquals(next, controller.conversation.value)
            assertEquals("game.a", persistedScope?.gamePackage)
            assertEquals("local", persistedScope?.userId)
            assertEquals(next, persistedNext)
            assertEquals(1234L, persistedTimestamp)
            assertTrue(persistedScope != null)
        } finally {
            ownerScope.cancel()
        }
    }
}
