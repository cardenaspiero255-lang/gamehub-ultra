package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UltraPhase5PrivateMemoryTest {
    @Test
    fun memoryRequiresExplicitConsentAndRevocationIsDurable() {
        val values = mutableMapOf<String, String>()
        val prefs = UltraMemoryPrivacyPreferences(
            read = values::get,
            write = { key, value -> values[key] = value },
            remove = { key -> values.remove(key) }
        )
        assertFalse(prefs.hasConsent())
        prefs.setConsent(true)
        assertTrue(prefs.hasConsent())
        val reloaded = UltraMemoryPrivacyPreferences(
            read = values::get,
            write = { key, value -> values[key] = value },
            remove = { key -> values.remove(key) }
        )
        assertTrue(reloaded.hasConsent())
        reloaded.setConsent(false)
        assertFalse(prefs.hasConsent())
    }

    @Test
    fun retentionOnlyAcceptsExplicitBoundedOptions() {
        val values = mutableMapOf<String, String>()
        val prefs = UltraMemoryPrivacyPreferences(
            read = values::get,
            write = { key, value -> values[key] = value },
            remove = { key -> values.remove(key) }
        )
        assertEquals(30, prefs.retentionDays())
        assertFalse(prefs.setRetentionDays(0))
        assertFalse(prefs.setRetentionDays(365))
        assertTrue(prefs.setRetentionDays(7))
        assertEquals(7, prefs.retentionDays())
        assertTrue(prefs.setRetentionDays(90))
        assertEquals(90, prefs.retentionDays())
    }

    @Test
    fun expiredConversationsArePurgedButExplicitFactsRemain() {
        val day = 86_400_000L
        val storage = TestPersistence()
        val scope = UltraMemoryScope(userId = "local", gamePackage = "game.a")
        storage.write(UltraMemoryCodec.encode(UltraMemorySnapshot(records = listOf(
            UltraStoredMemory("old", UltraMemoryKind.CONVERSATION, UltraMemoryRole.USER, "secreto viejo", 1L, scope),
            UltraStoredMemory("fresh", UltraMemoryKind.CONVERSATION, UltraMemoryRole.USER, "reciente", 39 * day, scope),
            UltraStoredMemory("fact", UltraMemoryKind.FACT, UltraMemoryRole.SYSTEM, "preferencia personal", 1L, scope),
            UltraStoredMemory("other-game", UltraMemoryKind.CONVERSATION, UltraMemoryRole.USER, "otro juego", 1L, scope.copy(gamePackage = "game.b"))
        ))))
        val repo = UltraMemoryRepository(storage, nowMillis = { 40 * day })
        assertEquals(2, repo.pruneExpiredConversations(retentionDays = 7))
        assertEquals(setOf("fresh", "fact"), repo.snapshot().records.map { it.id }.toSet())
        assertEquals(0, repo.pruneExpiredConversations(retentionDays = 7))
        assertEquals(2, UltraMemoryCodec.decode(storage.read()!!).records.size)
    }

    @Test
    fun deletingAllMemoriesDeletesEncryptedPayloadRatherThanLeavingSerializedEmptyData() {
        val storage = TestPersistence()
        val repo = UltraMemoryRepository(storage, nowMillis = { 100L })
        repo.syncConversation(emptyList(), listOf("Tú: dato privado"), UltraMemoryScope())
        assertTrue(storage.read() != null)
        repo.purgeAllData()
        assertEquals(null, storage.read())
        assertTrue(repo.snapshot().records.isEmpty())
    }

    private class TestPersistence : UltraMemoryPersistence {
        private var serialized: String? = null
        override fun read(): String? = serialized
        override fun write(serialized: String) { this.serialized = serialized }
        override fun clear() { serialized = null }
    }
}
