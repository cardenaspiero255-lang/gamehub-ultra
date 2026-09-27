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
    @Test
    fun lateMemoryLoadDoesNotOverwriteConversationUpdatedAfterLoadStarted() = runBlocking {
        val ownerScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val loadStarted = CompletableDeferred<Unit>()
        val releaseLoad = CompletableDeferred<Unit>()

        val memory = object : UltraAssistantSessionMemory {
            override suspend fun warmUp() = Unit

            override suspend fun recentConversationLines(
                limit: Int,
                scope: UltraMemoryScope
            ): List<String> {
                loadStarted.complete(Unit)
                releaseLoad.await()
                return listOf("Ultra: historial antiguo")
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

            val load = controller.selectGame("game.a")
            loadStarted.await()
            controller.updateConversation(listOf("Tú: mensaje nuevo"))

            releaseLoad.complete(Unit)
            load.join()

            assertEquals(
                listOf("Tú: mensaje nuevo"),
                controller.conversation.value
            )
        } finally {
            ownerScope.cancel()
        }
    }

    @Test
    fun clearingConversationClearsOnlyCurrentGameMemory() = runBlocking {
        val ownerScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var clearedScope: UltraMemoryScope? = null

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
            ) = Unit

            override fun enqueueClearConversationHistory(scope: UltraMemoryScope) {
                clearedScope = scope
            }
        }

        try {
            val controller = UltraAssistantSessionController(
                ownerScope = ownerScope,
                memory = memory,
                maxHistory = 8,
                ioDispatcher = Dispatchers.Unconfined,
                publicationDispatcher = Dispatchers.Unconfined
            )
            controller.selectGame("game.b").join()
            controller.updateConversation(emptyList())

            assertEquals("game.b", clearedScope?.gamePackage)
            assertEquals("local", clearedScope?.userId)
            assertTrue(controller.conversation.value.isEmpty())
        } finally {
            ownerScope.cancel()
        }
    }

    @Test
    fun restoredConversationSurvivesFirstSelectionAfterConfigurationRecreation() = runBlocking {
        val ownerScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var loadCalls = 0
        val restored = listOf(
            "Tú: borra el historial",
            "Ultra: Historial borrado."
        )

        val memory = object : UltraAssistantSessionMemory {
            override suspend fun warmUp() = Unit

            override suspend fun recentConversationLines(
                limit: Int,
                scope: UltraMemoryScope
            ): List<String> {
                loadCalls += 1
                return listOf("Ultra: historial persistido anterior")
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
                publicationDispatcher = Dispatchers.Unconfined,
                restoredGamePackage = "game.a",
                restoredConversation = restored
            )

            controller.selectGame("game.a").join()

            assertEquals(restored, controller.conversation.value)
            assertEquals(0, loadCalls)
        } finally {
            ownerScope.cancel()
        }
    }

    @Test
    fun storageFailureIsExposedAndRetryCanRecoverHistory() = runBlocking {
        val ownerScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var attempts = 0

        val memory = object : UltraAssistantSessionMemory {
            override suspend fun warmUp() = Unit

            override suspend fun recentConversationLines(
                limit: Int,
                scope: UltraMemoryScope
            ): List<String> {
                attempts += 1
                if (attempts == 1) error("storage unavailable")
                return listOf("Ultra: historial recuperado")
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

            controller.selectGame("game.a").join()

            assertEquals("storage unavailable", controller.loadError.value?.message)
            assertTrue(controller.conversation.value.isEmpty())

            controller.retryLoad().join()

            assertEquals(null, controller.loadError.value)
            assertEquals(
                listOf("Ultra: historial recuperado"),
                controller.conversation.value
            )
            assertEquals(2, attempts)
        } finally {
            ownerScope.cancel()
        }
    }

    @Test
    fun successfulRetryKeepsLoadErrorWhenRecoveredHistoryCannotBeApplied() = runBlocking {
        val ownerScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var attempts = 0

        val memory = object : UltraAssistantSessionMemory {
            override suspend fun warmUp() = Unit

            override suspend fun recentConversationLines(
                limit: Int,
                scope: UltraMemoryScope
            ): List<String> {
                attempts += 1
                if (attempts == 1) error("storage unavailable")
                return listOf("Ultra: historial recuperado")
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

            controller.selectGame("game.a").join()
            assertEquals("storage unavailable", controller.loadError.value?.message)

            controller.updateConversation(listOf("Tú: mensaje nuevo"))
            controller.retryLoad().join()

            assertEquals(
                listOf("Tú: mensaje nuevo"),
                controller.conversation.value
            )
            assertEquals(
                "storage unavailable",
                controller.loadError.value?.message
            )
            assertEquals(2, attempts)
        } finally {
            ownerScope.cancel()
        }
    }

}
