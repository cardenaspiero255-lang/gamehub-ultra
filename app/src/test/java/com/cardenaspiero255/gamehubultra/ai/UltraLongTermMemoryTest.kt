package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UltraLongTermMemoryTest {
    private val localScope = UltraMemoryScope(
        userId = "local",
        gamePackage = "com.example.game"
    )

    @Test
    fun rememberCommandExtractsFactWithoutWakeWordNoise() {
        val command = UltraMemoryCommandParser.parse(
            "Ultra, recuerda que prefiero el perfil X4"
        )

        val remember = assertIs<UltraMemoryCommand.Remember>(command)
        assertEquals("prefiero el perfil X4", remember.fact)
    }

    @Test
    fun secretLikeContentIsNeverEligibleForPersistentMemory() {
        assertFalse(UltraMemorySafety.canPersist("mi contraseña es hunter2"))
        assertFalse(UltraMemorySafety.canPersist("API token: abc123"))
        assertTrue(UltraMemorySafety.canPersist("prefiero jugar a 120 Hz"))
    }

    @Test
    fun retrievalUsesGlobalAndCurrentGameMemoryButNotOtherGames() {
        val records = listOf(
            UltraStoredMemory(
                id = "global",
                kind = UltraMemoryKind.FACT,
                role = UltraMemoryRole.SYSTEM,
                text = "prefiero respuestas cortas",
                timestampMillis = 1L,
                scope = UltraMemoryScope(userId = "local", gamePackage = null)
            ),
            UltraStoredMemory(
                id = "same-game",
                kind = UltraMemoryKind.FACT,
                role = UltraMemoryRole.SYSTEM,
                text = "en este juego prefiero el perfil X4",
                timestampMillis = 2L,
                scope = localScope
            ),
            UltraStoredMemory(
                id = "other-game",
                kind = UltraMemoryKind.FACT,
                role = UltraMemoryRole.SYSTEM,
                text = "en otro juego uso modo ahorro",
                timestampMillis = 3L,
                scope = UltraMemoryScope(
                    userId = "local",
                    gamePackage = "com.example.other"
                )
            )
        )

        val recalls = UltraMemoryRetrieval.relevant(
            query = "qué perfil prefiero para este juego",
            records = records,
            scope = localScope,
            limit = 10
        )

        assertTrue(recalls.any { it.record.id == "global" })
        assertTrue(recalls.any { it.record.id == "same-game" })
        assertFalse(recalls.any { it.record.id == "other-game" })
    }

    @Test
    fun archivedMemoryIsExcludedFromNormalRecall() {
        val records = listOf(
            UltraStoredMemory(
                id = "archived",
                kind = UltraMemoryKind.FACT,
                role = UltraMemoryRole.SYSTEM,
                text = "prefiero el perfil equilibrado",
                timestampMillis = 1L,
                scope = localScope,
                archived = true
            )
        )

        val recalls = UltraMemoryRetrieval.relevant(
            query = "qué perfil prefiero",
            records = records,
            scope = localScope
        )

        assertTrue(recalls.isEmpty())
    }

    @Test
    fun conversationDeltaDetectsOnlyNewTailAfterHistoryWindowShifts() {
        val previous = listOf(
            "Tú: uno",
            "Ultra: dos",
            "Tú: tres",
            "Ultra: cuatro"
        )
        val next = listOf(
            "Ultra: dos",
            "Tú: tres",
            "Ultra: cuatro",
            "Tú: cinco"
        )

        assertEquals(
            listOf("Tú: cinco"),
            UltraConversationDelta.newEntries(previous, next)
        )
    }

    @Test
    fun snapshotCodecRoundTripsAllFields() {
        val snapshot = UltraMemorySnapshot(
            enabled = true,
            records = listOf(
                UltraStoredMemory(
                    id = "1",
                    kind = UltraMemoryKind.CONVERSATION,
                    role = UltraMemoryRole.USER,
                    text = "me gusta RE4",
                    timestampMillis = 1234L,
                    scope = localScope,
                    threadId = "default",
                    pinned = true
                )
            )
        )

        assertEquals(
            snapshot,
            UltraMemoryCodec.decode(UltraMemoryCodec.encode(snapshot))
        )
    }

    @Test
    fun clearAndToggleCommandsAreRecognizedOffline() {
        assertIs<UltraMemoryCommand.ClearHistory>(
            UltraMemoryCommandParser.parse("Ultra borra mi historial")
        )
        assertIs<UltraMemoryCommand.Disable>(
            UltraMemoryCommandParser.parse("Ultra desactiva la memoria")
        )
        assertIs<UltraMemoryCommand.Enable>(
            UltraMemoryCommandParser.parse("Ultra activa la memoria")
        )
    }
}
