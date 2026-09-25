package com.cardenaspiero255.gamehubultra.data

import android.content.Context
import com.cardenaspiero255.gamehubultra.ai.UltraLongTermMemoryGateway
import com.cardenaspiero255.gamehubultra.ai.UltraMemoryPersistence
import com.cardenaspiero255.gamehubultra.ai.UltraMemoryRecall
import com.cardenaspiero255.gamehubultra.ai.UltraMemoryRepository
import com.cardenaspiero255.gamehubultra.ai.UltraMemoryScope
import com.cardenaspiero255.gamehubultra.ai.UltraMemorySnapshot
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

private class EncryptedUltraMemoryPersistence(
    context: Context
) : UltraMemoryPersistence {
    private val secureStore = SecureCredentialStore(context.applicationContext)

    override fun read(): String? = secureStore.get(MEMORY_KEY)

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

class UltraConversationMemoryStore private constructor(
    context: Context
) : UltraLongTermMemoryGateway {
    private val appContext = context.applicationContext
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val repositoryFuture: Future<UltraMemoryRepository> = executor.submit<UltraMemoryRepository> {
        UltraMemoryRepository(
            persistence = EncryptedUltraMemoryPersistence(appContext)
        )
    }

    private fun repository(): UltraMemoryRepository = repositoryFuture.get()

    fun warmUp() {
        repository()
    }

    override fun handleCommand(
        message: String,
        scope: UltraMemoryScope
    ): String? = repository().handleCommand(message, scope)

    override fun recallContext(
        message: String,
        scope: UltraMemoryScope,
        limit: Int
    ): List<UltraMemoryRecall> = repository().recallContext(message, scope, limit)

    fun snapshot(): UltraMemorySnapshot = repository().snapshot()

    fun syncConversation(
        previous: List<String>,
        next: List<String>,
        scope: UltraMemoryScope,
        threadId: String = "default",
        timestampMillis: Long? = null
    ) {
        repository().syncConversation(
            previous = previous,
            next = next,
            scope = scope,
            threadId = threadId,
            timestampMillis = timestampMillis
        )
    }

    fun enqueueSyncConversation(
        previous: List<String>,
        next: List<String>,
        scope: UltraMemoryScope,
        threadId: String = "default",
        timestampMillis: Long? = null
    ) {
        executor.execute {
            syncConversation(previous, next, scope, threadId, timestampMillis)
        }
    }

    fun recentConversationLines(
        limit: Int,
        scope: UltraMemoryScope = UltraMemoryScope()
    ): List<String> = repository().recentConversationLines(limit, scope)

    fun clearConversationHistory(userId: String = "local") {
        repository().clearConversationHistory(userId)
    }

    fun enqueueClearConversationHistory(userId: String = "local") {
        executor.execute {
            clearConversationHistory(userId)
        }
    }

    fun exportSnapshot(): String = repository().exportSnapshot()

    fun importSnapshot(serialized: String): Boolean = repository().importSnapshot(serialized)

    fun search(
        query: String,
        scope: UltraMemoryScope,
        limit: Int = 20
    ): List<UltraMemoryRecall> = repository().search(query, scope, limit)

    companion object {
        @Volatile
        private var instance: UltraConversationMemoryStore? = null

        fun get(context: Context): UltraConversationMemoryStore =
            instance ?: synchronized(this) {
                instance ?: UltraConversationMemoryStore(context.applicationContext).also {
                    instance = it
                }
            }
    }
}
