package com.cardenaspiero255.gamehubultra

import com.cardenaspiero255.gamehubultra.ai.UltraMemoryScope
import com.cardenaspiero255.gamehubultra.data.UltraConversationMemoryStore
import org.mockito.Mockito
import kotlin.test.Test

class UltraPhase5ConsentHistoryResetTest {
    @Test
    fun disablingMemoryDoesNotTreatTransientChatResetAsStoredHistoryDeletion() {
        val store = Mockito.mock(UltraConversationMemoryStore::class.java)
        Mockito.`when`(store.isMemoryEnabled()).thenReturn(false)
        val adapter = UltraConversationSessionMemoryAdapter(store)

        adapter.enqueueClearConversationHistory(UltraMemoryScope())

        Mockito.verify(store, Mockito.never())
            .enqueueClearConversationHistory(Mockito.any(UltraMemoryScope::class.java))
    }

    @Test
    fun explicitConsentPreservesExistingDeleteOnChatClearBehavior() {
        val store = Mockito.mock(UltraConversationMemoryStore::class.java)
        Mockito.`when`(store.isMemoryEnabled()).thenReturn(true)
        val adapter = UltraConversationSessionMemoryAdapter(store)
        val scope = UltraMemoryScope(gamePackage = "com.example.game")

        adapter.enqueueClearConversationHistory(scope)

        Mockito.verify(store).enqueueClearConversationHistory(scope)
    }
}
