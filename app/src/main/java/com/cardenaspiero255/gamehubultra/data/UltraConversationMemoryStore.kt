package com.cardenaspiero255.gamehubultra.data

import android.content.Context
import com.cardenaspiero255.gamehubultra.ai.UltraLongTermMemoryGateway
import com.cardenaspiero255.gamehubultra.ai.UltraMemoryPersistence
import com.cardenaspiero255.gamehubultra.ai.UltraMemoryRecall
import com.cardenaspiero255.gamehubultra.ai.UltraMemoryRepository
import com.cardenaspiero255.gamehubultra.ai.UltraMemoryScope
import com.cardenaspiero255.gamehubultra.ai.UltraMemorySnapshot

private class EncryptedUltraMemoryPersistence(
    context: Context
) : UltraMemoryPersistence {
    private val secureStore = SecureCredentialStore(context.applicationContext)

    override fun read(): String? =
        secureStore.get(MEMORY_KEY)

    override fun write(serialized: String) {
        secureStore.put(MEMORY_KEY, serialized)
    }

    override fun clear() {
        secureStore.remove(MEMORY_KEY)
    }

    private companion object {
        const val MEMORY_KEY = "ultra_conversation_memory_v1"
    }
}

class UltraConversationMemoryStore(
    context: Context
) : UltraLongTermMemoryGateway {
    private val repository = UltraMemoryRepository(
        persistence = EncryptedUltraMemoryPersistence(context)
    )

    override fun handleCommand(
        message: String,
        scope: UltraMemoryScope
    ): String? =
        repository.handleCommand(message, scope)

    override fun recallContext(
        message: String,
        scope: UltraMemoryScope,
        limit: Int
    ): List<UltraMemoryRecall> =
        repository.recallContext(message, scope, limit)

    fun snapshot(): UltraMemorySnapshot =
        repository.snapshot()

    fun syncConversation(
        previous: List<String>,
        next: List<String>,
        scope: UltraMemoryScope,
        threadId: String = "default",
        timestampMillis: Long? = null
    ) {
        repository.syncConversation(
            previous = previous,
            next = next,
            scope = scope,
            threadId = threadId,
            timestampMillis = timestampMillis
        )
    }

    fun recentConversationLines(limit: Int): List<String> =
        repository.recentConversationLines(limit)

    fun clearConversationHistory(userId: String = "local") {
        repository.clearConversationHistory(userId)
    }

    fun exportSnapshot(): String =
        repository.exportSnapshot()

    fun importSnapshot(serialized: String): Boolean =
        repository.importSnapshot(serialized)

    fun search(
        query: String,
        scope: UltraMemoryScope,
        limit: Int = 20
    ): List<UltraMemoryRecall> =
        repository.search(query, scope, limit)
}
