package com.cardenaspiero255.gamehubultra.data

import android.content.Context
import com.cardenaspiero255.gamehubultra.ai.UltraLongTermMemoryGateway
import com.cardenaspiero255.gamehubultra.ai.UltraMemoryPersistence
import com.cardenaspiero255.gamehubultra.ai.UltraMemoryRecall
import com.cardenaspiero255.gamehubultra.ai.UltraMemoryRepository
import com.cardenaspiero255.gamehubultra.ai.UltraMemoryScope
import com.cardenaspiero255.gamehubultra.ai.UltraMemorySnapshot
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

internal class UltraMemoryRepositoryProvider(
    private val executor: ExecutorService,
    private val factory: () -> UltraMemoryRepository
) {
    @Volatile
    private var repositoryFuture: Future<UltraMemoryRepository>? = null

    fun get(): UltraMemoryRepository {
        val future = currentFuture()
        return try {
            future.get()
        } catch (error: ExecutionException) {
            invalidate(future)
            when (val cause = error.cause) {
                is RuntimeException -> throw cause
                is Error -> throw cause
                else -> throw IllegalStateException(
                    "Ultra memory repository initialization failed",
                    cause ?: error
                )
            }
        }
    }

    private fun currentFuture(): Future<UltraMemoryRepository> =
        repositoryFuture ?: synchronized(this) {
            repositoryFuture ?: executor.submit(factory).also {
                repositoryFuture = it
            }
        }

    private fun invalidate(future: Future<UltraMemoryRepository>) {
        synchronized(this) {
            if (repositoryFuture === future) {
                repositoryFuture = null
            }
        }
    }
}

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
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread(task, "UltraMemoryStore").apply {
            isDaemon = true
        }
    }
    private val repositoryProvider = UltraMemoryRepositoryProvider(executor) {
        UltraMemoryRepository(
            persistence = EncryptedUltraMemoryPersistence(appContext)
        )
    }

    private fun repository(): UltraMemoryRepository = repositoryProvider.get()

    fun warmUp() {
        repository()
    }

    override fun handleCommand(
        message: String,
        scope: UltraMemoryScope
    ): String? =
        executor.submit<String?> {
            repository().handleCommand(message, scope)
        }.get()

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

    fun clearConversationHistory(scope: UltraMemoryScope = UltraMemoryScope()) {
        repository().clearConversationHistory(scope)
    }

    fun clearConversationHistory(userId: String) {
        repository().clearConversationHistory(userId)
    }

    fun enqueueClearConversationHistory(scope: UltraMemoryScope = UltraMemoryScope()) {
        executor.execute {
            clearConversationHistory(scope)
        }
    }

    fun enqueueClearConversationHistory(userId: String) {
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
