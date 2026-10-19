package com.cardenaspiero255.gamehubultra.data

import com.cardenaspiero255.gamehubultra.ai.UltraMemoryCodec
import com.cardenaspiero255.gamehubultra.ai.UltraMemoryKind
import com.cardenaspiero255.gamehubultra.ai.UltraMemoryPersistence
import com.cardenaspiero255.gamehubultra.ai.UltraMemoryPrivacyPreferences
import com.cardenaspiero255.gamehubultra.ai.UltraMemoryRepository
import com.cardenaspiero255.gamehubultra.ai.UltraMemoryRole
import com.cardenaspiero255.gamehubultra.ai.UltraMemoryScope
import com.cardenaspiero255.gamehubultra.ai.UltraMemorySnapshot
import com.cardenaspiero255.gamehubultra.ai.UltraStoredMemory
import com.cardenaspiero255.gamehubultra.ai.UnavailableUltraMemoryPrivacyGateway
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

/** Real store/queue/policy integration; only Android Keystore I/O is replaced by in-memory fakes. */
class UltraConversationMemoryStoreContractTest {
    private val gameA = UltraMemoryScope(userId = "local", gamePackage = "game.a")
    private val gameB = UltraMemoryScope(userId = "local", gamePackage = "game.b")
    private val global = UltraMemoryScope(userId = "local")
    private val day = 86_400_000L

    @Test
    fun noConversationFactExportOrImportBeforeOptIn() {
        val f = Fixture()
        assertFalse(f.store.isMemoryEnabled())
        assertEquals(30, f.store.conversationRetentionDays())
        f.store.syncConversation(emptyList(), listOf("Tú: prefiero perfil rápido"), gameA)
        f.store.enqueueSyncConversation(emptyList(), listOf("Ultra: turno tardío"), gameA)
        f.store.clearConversationHistory(gameA) // drains the prior queued action
        assertNull(f.persistence.read())
        assertTrue(f.store.handleCommand("recuerda que prefiero X4", gameA)!!.contains("desactivada"))
        assertNull(f.store.handleCommand("un mensaje sin orden", gameA))
        assertTrue(f.store.recallContext("perfil", gameA, 5).isEmpty())
        assertTrue(f.store.recentConversationLines(10, gameA).isEmpty())
        assertTrue(f.store.search("perfil", gameA).isEmpty())
        assertFalse(f.store.snapshot().enabled)
        assertEquals(
            UltraMemoryCodec.encode(UltraMemorySnapshot(enabled = false)),
            f.store.exportSnapshot()
        )
        assertFalse(f.store.importSnapshot(UltraMemoryCodec.encode(UltraMemorySnapshot())))
        assertNull(f.persistence.read())
    }

    @Test
    fun optingInPersistsScopedConversationsAndRevocationBlocksAllReadsAndWrites() {
        val f = Fixture()
        f.store.setMemoryEnabled(true)
        assertTrue(f.store.isMemoryEnabled())
        assertNotNull(f.persistence.read())
        f.store.syncConversation(
            previous = emptyList(),
            next = listOf("Tú: prefiero X4", "Ultra: entendido"),
            scope = gameA,
            threadId = "match-1"
        )
        assertEquals(listOf("Tú: prefiero X4", "Ultra: entendido"), f.store.recentConversationLines(10, gameA))
        assertTrue(f.store.recallContext("prefiero X4", gameA, 5).isNotEmpty())
        assertTrue(f.store.search("prefiero X4", gameA, 5).isNotEmpty())
        assertTrue(f.store.snapshot().records.isNotEmpty())
        assertTrue(f.store.exportSnapshot().contains("ULTRA_MEMORY_V1|"))
        f.store.setMemoryEnabled(false)
        assertFalse(f.store.isMemoryEnabled())
        assertTrue(f.store.snapshot().records.isEmpty())
        assertTrue(f.store.recallContext("prefiero X4", gameA, 5).isEmpty())
        assertTrue(f.store.recentConversationLines(10, gameA).isEmpty())
        assertTrue(f.store.search("prefiero X4", gameA).isEmpty())
        val existing = f.persistence.read()
        assertNotNull(existing) // revocation prevents access; it is NOT an erase request
        f.store.syncConversation(emptyList(), listOf("Tú: no guardar"), gameA)
        f.store.enqueueSyncConversation(emptyList(), listOf("Tú: en cola"), gameA)
        f.store.enqueueClearConversationHistory(gameA)
        f.store.setMemoryEnabled(false) // serialized barrier
        assertEquals(existing, f.persistence.read())
        f.store.deleteAllPrivateMemory()
        assertFalse(f.store.isMemoryEnabled())
        assertNull(f.persistence.read())
        f.store.clearConversationHistory(gameA)
        f.store.enqueueSyncConversation(emptyList(), listOf("Tú: después del borrado"), gameA)
        f.store.clearConversationHistory(gameA)
        assertNull(f.persistence.read()) // no late callback resurrects encrypted payload
    }

    @Test
    fun retentionPrunesOldConversationsButPreservesExplicitFacts() {
        val f = Fixture(
            initial = UltraMemoryCodec.encode(
                UltraMemorySnapshot(
                    records = listOf(
                        record("old", UltraMemoryKind.CONVERSATION, 1 * day, gameA),
                        record("mid", UltraMemoryKind.CONVERSATION, 20 * day, gameA),
                        record("fresh", UltraMemoryKind.CONVERSATION, 39 * day, gameB),
                        record("fact", UltraMemoryKind.FACT, 1 * day, gameA)
                    )
                )
            )
        )
        f.store.warmUp() // 30 days, regardless of the consent setting
        assertEquals(setOf("mid", "fresh", "fact"), f.repo.snapshot().records.map { it.id }.toSet())
        assertFalse(f.store.setConversationRetentionDays(0))
        assertFalse(f.store.setConversationRetentionDays(365))
        assertEquals(30, f.store.conversationRetentionDays())
        assertTrue(f.store.setConversationRetentionDays(7))
        assertEquals(7, f.store.conversationRetentionDays())
        assertEquals(setOf("fresh", "fact"), f.repo.snapshot().records.map { it.id }.toSet())
        assertTrue(f.store.setConversationRetentionDays(90))
        assertEquals(90, f.store.conversationRetentionDays())
        f.store.setMemoryEnabled(true)
        assertEquals(setOf("fresh", "fact"), f.store.snapshot().records.map { it.id }.toSet())
    }

    @Test
    fun voiceCommandsRespectConsentPinArchiveAndClearAll() {
        val f = Fixture()
        assertTrue(f.store.handleCommand("recuerda que prefiero jugar X4", gameA)!!.contains("desactivada"))
        assertNotNull(f.store.handleCommand("activa la memoria", gameA))
        assertTrue(f.store.isMemoryEnabled())
        assertTrue(f.store.handleCommand("recuerda que prefiero jugar X4", gameA)!!.contains("recordar"))
        assertTrue(f.store.handleCommand("fija prefiero jugar X4", gameA)!!.contains("Fijé"))
        assertTrue(f.store.snapshot().records.any { it.pinned })
        assertTrue(f.store.handleCommand("archiva prefiero jugar X4", gameA)!!.contains("Archivé"))
        assertTrue(f.store.snapshot().records.any { it.archived })
        assertTrue(f.store.recallContext("prefiero jugar X4", gameA, 10).isEmpty())
        assertNotNull(f.store.handleCommand("desactiva la memoria", gameA))
        assertFalse(f.store.isMemoryEnabled())
        assertTrue(f.store.snapshot().records.isEmpty())
        assertNotNull(f.store.handleCommand("activa la memoria", gameA))
        assertTrue(f.store.snapshot().records.any { it.archived })
        assertEquals(
            "Borré toda la memoria local cifrada de Ultra y desactivé su guardado.",
            f.store.handleCommand("borra toda mi memoria", gameA)
        )
        assertFalse(f.store.isMemoryEnabled())
        assertNull(f.persistence.read())
    }

    @Test
    fun scopedClearHistoryKeepsFactsAndConversationsFromOtherGames() {
        val f = Fixture()
        f.store.setMemoryEnabled(true)
        f.store.syncConversation(emptyList(), listOf("Tú: partida A"), gameA)
        f.store.syncConversation(emptyList(), listOf("Tú: partida B"), gameB)
        f.store.syncConversation(emptyList(), listOf("Tú: sin juego"), global)
        f.store.syncConversation(emptyList(), listOf("Tú: otro usuario"), UltraMemoryScope("otro", "game.a"))
        assertTrue(f.store.handleCommand("recuerda que prefiero jugar X4", gameA)!!.contains("recordar"))
        f.store.clearConversationHistory(gameA)
        assertTrue(f.store.recentConversationLines(10, gameA).isEmpty())
        assertEquals(listOf("Tú: partida B"), f.store.recentConversationLines(10, gameB))
        assertTrue(f.store.snapshot().records.any { it.kind == UltraMemoryKind.FACT && it.scope == gameA })
        f.store.clearConversationHistory()
        assertTrue(f.store.recentConversationLines(10, global).isEmpty())
        f.store.clearConversationHistory("otro")
        assertTrue(f.store.snapshot().records.none { it.scope.userId == "otro" })
        assertTrue(f.store.snapshot().records.any { it.scope == gameB })
        f.store.enqueueClearConversationHistory(gameB)
        f.store.setConversationRetentionDays(30) // drains queued clear
        assertTrue(f.store.recentConversationLines(10, gameB).isEmpty())
    }

    @Test
    fun snapshotImportAndSearchAreOnlyAvailableWithConsent() {
        val f = Fixture()
        val raw = UltraMemoryCodec.encode(
            UltraMemorySnapshot(records = listOf(
                record("imported", UltraMemoryKind.FACT, 39 * day, gameA)
            ))
        )
        assertFalse(f.store.importSnapshot(raw))
        f.store.setMemoryEnabled(true)
        assertFalse(f.store.importSnapshot("bad snapshot"))
        assertTrue(f.store.importSnapshot(raw))
        assertEquals("imported", f.store.search("imported", gameA).single().record.id)
        assertTrue(f.store.search("imported", gameB).isEmpty())
        assertEquals("imported", f.store.recallContext("imported", gameA, 5).single().record.id)
        assertEquals(listOf("imported"), UltraMemoryCodec.decode(f.store.exportSnapshot()).records.map { it.id })
        assertTrue(f.store.handleCommand("borra mi historial", gameA)!!.contains("borrado"))
        assertEquals(listOf("imported"), f.store.snapshot().records.map { it.id })
        f.store.deleteAllPrivateMemory()
        assertFalse(f.store.importSnapshot(raw))
        assertNull(f.persistence.read())
    }

    @Test
    fun absentPrivacyGatewayFailsClosedInsteadOfClaimingSuccess() {
        val unavailable = UnavailableUltraMemoryPrivacyGateway
        assertFalse(unavailable.isMemoryEnabled())
        assertEquals(30, unavailable.conversationRetentionDays())
        assertFalse(unavailable.setConversationRetentionDays(7))
        assertFailsWith<IllegalStateException> { unavailable.setMemoryEnabled(true) }
        assertFailsWith<IllegalStateException> { unavailable.clearConversationHistory(gameA) }
        assertFailsWith<IllegalStateException> { unavailable.deleteAllPrivateMemory() }
    }

    private fun record(id: String, kind: UltraMemoryKind, timestamp: Long, scope: UltraMemoryScope) =
        UltraStoredMemory(
            id = id, kind = kind, role = UltraMemoryRole.USER,
            text = if (id == "imported") "imported" else id,
            timestampMillis = timestamp, scope = scope
        )

    private inner class Fixture(initial: String? = null) {
        private val preferencesData = ConcurrentHashMap<String, String>()
        val persistence = TestPersistence().apply { initial?.let(::write) }
        private val nextId = AtomicInteger()
        val repo = UltraMemoryRepository(
            persistence = persistence,
            nowMillis = { 40 * day },
            idFactory = { "test-${nextId.incrementAndGet()}" }
        )
        val preferences = UltraMemoryPrivacyPreferences(
            read = preferencesData::get,
            write = { key, value -> preferencesData[key] = value },
            remove = { key -> preferencesData.remove(key) }
        )
        val store = UltraConversationMemoryStore(preferences) { repo }
    }

    private class TestPersistence : UltraMemoryPersistence {
        @Volatile private var payload: String? = null
        override fun read(): String? = payload
        override fun write(serialized: String) { payload = serialized }
        override fun clear() { payload = null }
    }
}
