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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UltraAssistantSessionControllerTest {

    @Test
    fun switchingGamesCannotPublishStaleHistory() = runBlocking {
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
                        listOf("Ultra: historial A")
                    }
                    "game.b" -> listOf("Ultra: historial B")
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
                maxHistory = 20,
                ioDispatcher = Dispatchers.Unconfined,
                publicationDispatcher = Dispatchers.Unconfined
            )

            val first = controller.selectGame("game.a")
            gameAStarted.await()
            val second = controller.selectGame("game.b")
            second.join()
            releaseGameA.complete(Unit)
            first.join()

            assertEquals("game.b", controller.selectedGamePackage.value)
            assertEquals(listOf("Ultra: historial B"), controller.conversation.value)
        } finally {
            ownerScope.cancel()
        }
    }

    @Test
    fun lateHistoryLoadCannotOverwriteConversationCreatedAfterLoadStarted() = runBlocking {
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
                return listOf("Ultra: historial viejo")
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
                maxHistory = 20,
                ioDispatcher = Dispatchers.Unconfined,
                publicationDispatcher = Dispatchers.Unconfined
            )

            val load = controller.selectGame("game.a")
            loadStarted.await()
            controller.updateConversation(listOf("Tú: mensaje nuevo"))
            releaseLoad.complete(Unit)
            load.join()

            assertEquals(listOf("Tú: mensaje nuevo"), controller.conversation.value)
        } finally {
            ownerScope.cancel()
        }
    }

    @Test
    fun restoredConversationSurvivesFirstSameGameSelectionWithoutReload() = runBlocking {
        val ownerScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var readCalls = 0
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
                readCalls += 1
                return listOf("Ultra: persistido anterior")
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
                maxHistory = 20,
                ioDispatcher = Dispatchers.Unconfined,
                publicationDispatcher = Dispatchers.Unconfined,
                restoredGamePackage = "game.a",
                restoredConversation = restored
            )

            controller.selectGame("game.a").join()

            assertEquals(restored, controller.conversation.value)
            assertEquals(0, readCalls)
        } finally {
            ownerScope.cancel()
        }
    }

    @Test
    fun storageFailureIsVisibleAndRetryCanRecoverHistory() = runBlocking {
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
                maxHistory = 20,
                ioDispatcher = Dispatchers.Unconfined,
                publicationDispatcher = Dispatchers.Unconfined
            )

            controller.selectGame("game.a").join()
            assertEquals("storage unavailable", controller.loadError.value?.message)
            assertTrue(controller.conversation.value.isEmpty())

            controller.retryLoad().join()

            assertNull(controller.loadError.value)
            assertEquals(listOf("Ultra: historial recuperado"), controller.conversation.value)
            assertEquals(2, attempts)
        } finally {
            ownerScope.cancel()
        }
    }



    @Test
    fun explicitClearPersistsWhenVisibleConversationIsAlreadyEmptyAfterLoadFailure() = runBlocking {
        val ownerScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var attempts = 0
        var clearedScope: UltraMemoryScope? = null

        val memory = object : UltraAssistantSessionMemory {
            override suspend fun warmUp() = Unit

            override suspend fun recentConversationLines(
                limit: Int,
                scope: UltraMemoryScope
            ): List<String> {
                attempts += 1
                if (attempts == 1) error("storage unavailable")
                return listOf("Ultra: historial viejo")
            }

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
                maxHistory = 20,
                ioDispatcher = Dispatchers.Unconfined,
                publicationDispatcher = Dispatchers.Unconfined
            )

            controller.selectGame("game.a").join()
            assertTrue(controller.conversation.value.isEmpty())

            controller.updateConversation(emptyList())

            assertEquals("game.a", clearedScope?.gamePackage)
            assertEquals("local", clearedScope?.userId)

            controller.retryLoad().join()

            assertNull(controller.loadError.value)
            assertTrue(controller.conversation.value.isEmpty())
            assertEquals(2, attempts)
        } finally {
            ownerScope.cancel()
        }
    }

    @Test
    fun retryAfterFailureCannotOverwriteConversationCreatedBeforeRetry() = runBlocking {
        val ownerScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var attempts = 0
        val liveConversation = listOf(
            "Tú: mensaje nuevo",
            "Ultra: respuesta nueva"
        )

        val memory = object : UltraAssistantSessionMemory {
            override suspend fun warmUp() = Unit

            override suspend fun recentConversationLines(
                limit: Int,
                scope: UltraMemoryScope
            ): List<String> {
                attempts += 1
                if (attempts == 1) error("storage unavailable")
                return listOf("Ultra: historial viejo")
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
                maxHistory = 20,
                ioDispatcher = Dispatchers.Unconfined,
                publicationDispatcher = Dispatchers.Unconfined
            )

            controller.selectGame("game.a").join()
            assertEquals("storage unavailable", controller.loadError.value?.message)

            controller.updateConversation(liveConversation)
            controller.retryLoad().join()

            assertNull(controller.loadError.value)
            assertEquals(liveConversation, controller.conversation.value)
            assertEquals(2, attempts)
        } finally {
            ownerScope.cancel()
        }
    }

    @Test
    fun switchingGamePublishesClearedSnapshotBeforeHistoryLoadCompletes() = runBlocking {
        val ownerScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val loadStarted = CompletableDeferred<Unit>()
        val releaseLoad = CompletableDeferred<Unit>()
        val snapshots = mutableListOf<Pair<String?, List<String>>>()

        val memory = object : UltraAssistantSessionMemory {
            override suspend fun warmUp() = Unit

            override suspend fun recentConversationLines(
                limit: Int,
                scope: UltraMemoryScope
            ): List<String> {
                loadStarted.complete(Unit)
                releaseLoad.await()
                return listOf("Ultra: historial B")
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
                maxHistory = 20,
                ioDispatcher = Dispatchers.Unconfined,
                publicationDispatcher = Dispatchers.Unconfined,
                restoredGamePackage = "game.a",
                restoredConversation = listOf("Ultra: historial A"),
                onSnapshotChanged = { gamePackage, conversation ->
                    snapshots += gamePackage to conversation
                }
            )

            val load = controller.selectGame("game.b")
            loadStarted.await()

            assertEquals(
                listOf("game.b" to emptyList<String>()),
                snapshots
            )
            assertEquals("game.b", controller.selectedGamePackage.value)
            assertTrue(controller.conversation.value.isEmpty())

            releaseLoad.complete(Unit)
            load.join()

            assertEquals(
                listOf(
                    "game.b" to emptyList<String>(),
                    "game.b" to listOf("Ultra: historial B")
                ),
                snapshots
            )
        } finally {
            ownerScope.cancel()
        }
    }

    @Test
    fun updatesAndClearsUseTheControllersCurrentGameScope() = runBlocking {
        val ownerScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var persistedScope: UltraMemoryScope? = null
        var clearedScope: UltraMemoryScope? = null
        var persistedTimestamp = -1L
        val persistedTransitions = mutableListOf<Pair<List<String>, List<String>>>()

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
                persistedTimestamp = timestampMillis
                persistedTransitions += previous to next
            }

            override fun enqueueClearConversationHistory(scope: UltraMemoryScope) {
                clearedScope = scope
            }
        }

        try {
            val controller = UltraAssistantSessionController(
                ownerScope = ownerScope,
                memory = memory,
                maxHistory = 20,
                ioDispatcher = Dispatchers.Unconfined,
                publicationDispatcher = Dispatchers.Unconfined,
                nowMillis = { 1234L }
            )

            controller.selectGame("game.a").join()
            val first = listOf(
                "Tú: hola",
                "Ultra: Hola, ¿en qué te ayudo?"
            )
            val second = first + listOf(
                "Tú: dime los fps",
                "Ultra: Revisando rendimiento."
            )

            controller.updateConversation(first)
            controller.updateConversation(second)

            assertEquals("game.a", persistedScope?.gamePackage)
            assertEquals("local", persistedScope?.userId)
            assertEquals(1234L, persistedTimestamp)
            assertEquals(
                listOf(
                    emptyList<String>() to first,
                    first to second
                ),
                persistedTransitions
            )

            controller.updateConversation(emptyList())

            assertEquals("game.a", clearedScope?.gamePackage)
            assertEquals("local", clearedScope?.userId)
        } finally {
            ownerScope.cancel()
        }
    }
}
