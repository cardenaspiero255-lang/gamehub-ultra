package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UltraMemoryRepositoryTest {
    private val scope = UltraMemoryScope(
        userId = "local",
        gamePackage = "com.example.game"
    )

    @Test
    fun rememberCommandPersistsSafeFactAndCanRecallIt() {
        val persistence = InMemoryUltraMemoryPersistence()
        val repository = UltraMemoryRepository(
            persistence = persistence,
            nowMillis = { 100L },
            idFactory = { "fact-1" }
        )

        val answer = repository.handleCommand(
            "Ultra recuerda que prefiero X4",
            scope
        )

        assertTrue(answer.orEmpty().contains("recordaré", ignoreCase = true))
        val snapshot = repository.snapshot()
        assertEquals(1, snapshot.records.size)
        assertEquals(UltraMemoryKind.FACT, snapshot.records.single().kind)
        assertEquals("prefiero X4", snapshot.records.single().text)

        val recalled = repository.recallContext(
            message = "qué perfil prefiero",
            scope = scope
        )
        assertEquals("fact-1", recalled.single().record.id)
    }

    @Test
    fun secretLikeRememberCommandIsRejectedWithoutWriting() {
        val persistence = InMemoryUltraMemoryPersistence()
        val repository = UltraMemoryRepository(
            persistence = persistence,
            nowMillis = { 100L },
            idFactory = { "secret-1" }
        )

        val answer = repository.handleCommand(
            "Ultra recuerda que mi contraseña es hunter2",
            scope
        )

        assertTrue(answer.orEmpty().contains("no", ignoreCase = true))
        assertTrue(repository.snapshot().records.isEmpty())
    }

    @Test
    fun disabledMemoryDoesNotPersistConversationUntilEnabledAgain() {
        val repository = UltraMemoryRepository(
            persistence = InMemoryUltraMemoryPersistence(),
            nowMillis = { 100L },
            idFactory = sequenceIdFactory()
        )

        repository.handleCommand("Ultra desactiva la memoria", scope)
        repository.syncConversation(
            previous = emptyList(),
            next = listOf("Tú: hola", "Ultra: hola"),
            scope = scope
        )

        assertFalse(repository.snapshot().enabled)
        assertTrue(repository.snapshot().records.isEmpty())

        repository.handleCommand("Ultra activa la memoria", scope)
        repository.syncConversation(
            previous = emptyList(),
            next = listOf("Tú: hola", "Ultra: hola"),
            scope = scope
        )

        assertTrue(repository.snapshot().enabled)
        assertEquals(2, repository.snapshot().records.size)
    }

    @Test
    fun rollingConversationWindowOnlyPersistsNewTail() {
        val repository = UltraMemoryRepository(
            persistence = InMemoryUltraMemoryPersistence(),
            nowMillis = { 100L },
            idFactory = sequenceIdFactory()
        )

        val first = listOf(
            "Tú: uno",
            "Ultra: dos",
            "Tú: tres",
            "Ultra: cuatro"
        )
        repository.syncConversation(emptyList(), first, scope)

        val shifted = listOf(
            "Ultra: dos",
            "Tú: tres",
            "Ultra: cuatro",
            "Tú: cinco"
        )
        repository.syncConversation(first, shifted, scope)

        assertEquals(
            listOf("uno", "dos", "tres", "cuatro", "cinco"),
            repository.snapshot().records.map { it.text }
        )
    }

    @Test
    fun clearHistoryKeepsExplicitRememberedFacts() {
        val repository = UltraMemoryRepository(
            persistence = InMemoryUltraMemoryPersistence(),
            nowMillis = { 100L },
            idFactory = sequenceIdFactory()
        )

        repository.handleCommand("Ultra recuerda que prefiero X4", scope)
        repository.syncConversation(
            previous = emptyList(),
            next = listOf("Tú: hola", "Ultra: hola"),
            scope = scope
        )
        repository.handleCommand("Ultra borra mi historial", scope)

        val records = repository.snapshot().records
        assertEquals(1, records.size)
        assertEquals(UltraMemoryKind.FACT, records.single().kind)
    }

    @Test
    fun clearAllRemovesFactsAndConversation() {
        val repository = UltraMemoryRepository(
            persistence = InMemoryUltraMemoryPersistence(),
            nowMillis = { 100L },
            idFactory = sequenceIdFactory()
        )

        repository.handleCommand("Ultra recuerda que prefiero X4", scope)
        repository.syncConversation(
            previous = emptyList(),
            next = listOf("Tú: hola"),
            scope = scope
        )
        repository.handleCommand("Ultra olvida todo", scope)

        assertTrue(repository.snapshot().records.isEmpty())
    }

    @Test
    fun exportImportRoundTripPreservesMemoryState() {
        val firstPersistence = InMemoryUltraMemoryPersistence()
        val first = UltraMemoryRepository(
            persistence = firstPersistence,
            nowMillis = { 100L },
            idFactory = sequenceIdFactory()
        )
        first.handleCommand("Ultra recuerda que prefiero X4", scope)
        first.syncConversation(
            previous = emptyList(),
            next = listOf("Tú: hola", "Ultra: hola"),
            scope = scope
        )

        val exported = first.exportSnapshot()

        val second = UltraMemoryRepository(
            persistence = InMemoryUltraMemoryPersistence(),
            nowMillis = { 200L },
            idFactory = sequenceIdFactory()
        )
        assertTrue(second.importSnapshot(exported))
        assertEquals(first.snapshot(), second.snapshot())
    }

    @Test
    fun recentConversationLinesExcludeFactsAndRespectLimit() {
        val repository = UltraMemoryRepository(
            persistence = InMemoryUltraMemoryPersistence(),
            nowMillis = { 100L },
            idFactory = sequenceIdFactory()
        )

        repository.handleCommand("Ultra recuerda que prefiero X4", scope)
        repository.syncConversation(
            previous = emptyList(),
            next = listOf(
                "Tú: uno",
                "Ultra: dos",
                "Tú: tres"
            ),
            scope = scope
        )

        assertEquals(
            listOf("Ultra: dos", "Tú: tres"),
            repository.recentConversationLines(limit = 2)
        )
    }


    @Test
    fun forgetRemovesMatchingFactAndConversationCopies() {
        val repository = UltraMemoryRepository(
            persistence = InMemoryUltraMemoryPersistence(),
            nowMillis = { 100L },
            idFactory = sequenceIdFactory()
        )
        repository.handleCommand("Ultra recuerda que prefiero X4", scope)
        repository.syncConversation(
            previous = emptyList(),
            next = listOf(
                "Tú: prefiero X4",
                "Ultra: recordaré que prefiero X4"
            ),
            scope = scope
        )

        repository.handleCommand("Ultra olvida prefiero X4", scope)

        assertFalse(repository.snapshot().records.any {
            it.text.contains("prefiero X4", ignoreCase = true)
        })
    }

    private fun sequenceIdFactory(): () -> String {
        var value = 0
        return {
            value += 1
            "id-$value"
        }
    }

    private class InMemoryUltraMemoryPersistence : UltraMemoryPersistence {
        private var value: String? = null

        override fun read(): String? = value

        override fun write(serialized: String) {
            value = serialized
        }

        override fun clear() {
            value = null
        }
    }
}
