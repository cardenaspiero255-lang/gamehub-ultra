package com.cardenaspiero255.gamehubultra.data

import android.content.Context
import com.cardenaspiero255.gamehubultra.ai.UltraLongTermMemoryGateway
import com.cardenaspiero255.gamehubultra.ai.UltraMemoryCommand
import com.cardenaspiero255.gamehubultra.ai.UltraMemoryCommandParser
import com.cardenaspiero255.gamehubultra.ai.UltraMemoryPrivacyPreferences
import com.cardenaspiero255.gamehubultra.ai.UltraMemoryPersistence
import com.cardenaspiero255.gamehubultra.ai.UltraMemoryRecall
import com.cardenaspiero255.gamehubultra.ai.UltraMemoryRepository
import com.cardenaspiero255.gamehubultra.ai.UltraMemoryScope
import com.cardenaspiero255.gamehubultra.ai.UltraMemorySnapshot
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal class UltraMemoryRepositoryProvider(
    private val executor: ExecutorService,
    private val factory: () -> UltraMemoryRepository
) {
    @Volatile
    private var repository: UltraMemoryRepository? = null

    fun get(): UltraMemoryRepository =
        repository ?: synchronized(this) {
            repository ?: factory().also {
                repository = it
            }
        }

    fun prewarm() {
        executor.execute {
            runCatching { get() }
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
    private val privateSettings = SecureCredentialStore(appContext)
    private val privacy = UltraMemoryPrivacyPreferences(
        read = privateSettings::get,
        write = privateSettings::put,
        remove = privateSettings::remove
    )
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

    init {
        repositoryProvider.prewarm()
    }

    private fun repository(): UltraMemoryRepository = repositoryProvider.get()

    private fun prunedRepository(): UltraMemoryRepository = repository().also { repo ->
        repo.pruneExpiredConversations(privacy.retentionDays())
    }

    /** No opt-in marker exists on clean installs or legacy saved history. */
    fun isMemoryEnabled(): Boolean = privacy.hasConsent()

    fun conversationRetentionDays(): Int = privacy.retentionDays()

    /** Serialized with queued saves: an earlier queued turn cannot resurrect after a revoke. */
    fun setMemoryEnabled(enabled: Boolean) {
        executor.submit {
            if (enabled) {
                repository().handleCommand("activa la memoria", UltraMemoryScope())
                privacy.setConsent(true)
                prunedRepository()
            } else {
                privacy.setConsent(false)
                repository().handleCommand("desactiva la memoria", UltraMemoryScope())
            }
        }.get()
    }

    fun setConversationRetentionDays(days: Int): Boolean =
        executor.submit<Boolean> {
            if (!privacy.setRetentionDays(days)) return@submit false
            prunedRepository()
            true
        }.get()

    /** Deletes the encrypted payload and revokes consent with all pending writes drained. */
    fun deleteAllPrivateMemory() {
        executor.submit {
            privacy.setConsent(false)
            repository().purgeAllData()
        }.get()
    }

    fun warmUp() {
        repository()
    }

    override fun handleCommand(
        message: String,
        scope: UltraMemoryScope
    ): String? =
        executor.submit<String?> {
            when (UltraMemoryCommandParser.parse(message)) {
                UltraMemoryCommand.Enable -> {
                    repository().handleCommand(message, scope).also {
                        privacy.setConsent(true)
                        prunedRepository()
                    }
                }
                UltraMemoryCommand.Disable -> {
                    privacy.setConsent(false)
                    repository().handleCommand(message, scope)
                }
                UltraMemoryCommand.ClearAll -> {
                    privacy.setConsent(false)
                    repository().purgeAllData()
                    "Borré toda la memoria local cifrada de Ultra y desactivé su guardado."
                }
                is UltraMemoryCommand.Remember,
                is UltraMemoryCommand.Pin,
                is UltraMemoryCommand.Archive -> {
                    if (privacy.hasConsent()) prunedRepository().handleCommand(message, scope)
                    else "La memoria privada está desactivada. Actívala para guardar recuerdos."
                }
                else -> prunedRepository().handleCommand(message, scope)
            }
        }.get()

    override fun recallContext(
        message: String,
        scope: UltraMemoryScope,
        limit: Int
    ): List<UltraMemoryRecall> =
        if (privacy.hasConsent()) prunedRepository().recallContext(message, scope, limit)
        else emptyList()

    fun snapshot(): UltraMemorySnapshot =
        if (privacy.hasConsent()) prunedRepository().snapshot()
        else UltraMemorySnapshot(enabled = false)

    fun syncConversation(
        previous: List<String>,
        next: List<String>,
        scope: UltraMemoryScope,
        threadId: String = "default",
        timestampMillis: Long? = null
    ) {
        if (!privacy.hasConsent()) return
        prunedRepository().syncConversation(
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
    ): List<String> =
        if (privacy.hasConsent()) prunedRepository().recentConversationLines(limit, scope)
        else emptyList()

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

    fun exportSnapshot(): String =
        if (privacy.hasConsent()) prunedRepository().exportSnapshot()
        else com.cardenaspiero255.gamehubultra.ai.UltraMemoryCodec.encode(UltraMemorySnapshot(enabled = false))

    fun importSnapshot(serialized: String): Boolean =
        privacy.hasConsent() && repository().importSnapshot(serialized).also { imported ->
            if (imported) prunedRepository()
        }

    fun search(
        query: String,
        scope: UltraMemoryScope,
        limit: Int = 20
    ): List<UltraMemoryRecall> =
        if (privacy.hasConsent()) prunedRepository().search(query, scope, limit)
        else emptyList()

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
