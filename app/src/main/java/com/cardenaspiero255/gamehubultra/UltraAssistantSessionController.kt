package com.cardenaspiero255.gamehubultra

import com.cardenaspiero255.gamehubultra.ai.UltraMemoryScope
import com.cardenaspiero255.gamehubultra.ai.UltraMemoryTurnPersistencePolicy
import com.cardenaspiero255.gamehubultra.data.UltraConversationMemoryStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Minimal memory boundary used by the Ultra session owner.
 *
 * Keeping this boundary free of Android types makes the session lifecycle and
 * game-switch behavior directly unit-testable.
 */
internal interface UltraAssistantSessionMemory {
    suspend fun warmUp()

    suspend fun recentConversationLines(
        limit: Int,
        scope: UltraMemoryScope
    ): List<String>

    fun enqueueSyncConversation(
        previous: List<String>,
        next: List<String>,
        scope: UltraMemoryScope,
        timestampMillis: Long
    )

    fun enqueueClearConversationHistory(scope: UltraMemoryScope)
}

internal class UltraConversationSessionMemoryAdapter(
    private val store: UltraConversationMemoryStore
) : UltraAssistantSessionMemory {
    override suspend fun warmUp() {
        store.warmUp()
    }

    override suspend fun recentConversationLines(
        limit: Int,
        scope: UltraMemoryScope
    ): List<String> =
        store.recentConversationLines(
            limit = limit,
            scope = scope
        )

    override fun enqueueSyncConversation(
        previous: List<String>,
        next: List<String>,
        scope: UltraMemoryScope,
        timestampMillis: Long
    ) {
        store.enqueueSyncConversation(
            previous = previous,
            next = next,
            scope = scope,
            timestampMillis = timestampMillis
        )
    }

    override fun enqueueClearConversationHistory(scope: UltraMemoryScope) {
        store.enqueueClearConversationHistory(scope = scope)
    }
}

/**
 * Screen-level owner for Ultra's conversation session.
 *
 * It keeps game-scoped memory, persistence and query publication outside the
 * composable assistant card. Switching games invalidates older loads so stale
 * conversation state cannot leak into the newly selected game.
 */
internal class UltraAssistantSessionController(
    private val ownerScope: CoroutineScope,
    private val memory: UltraAssistantSessionMemory,
    private val maxHistory: Int,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val publicationDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private val _conversation = MutableStateFlow<List<String>>(emptyList())
    val conversation: StateFlow<List<String>> = _conversation.asStateFlow()

    private val _selectedGamePackage = MutableStateFlow<String?>(null)
    val selectedGamePackage: StateFlow<String?> = _selectedGamePackage.asStateFlow()

    private var loadGeneration = 0L
    private var conversationRevision = 0L
    private var loadJob: Job? = null

    val queryRunner = UltraAssistantQueryRunner(
        ownerScope = ownerScope,
        publicationDispatcher = publicationDispatcher,
        currentGamePackage = { _selectedGamePackage.value },
        currentConversation = { _conversation.value },
        publishConversation = ::updateConversation
    )

    fun selectGame(gamePackage: String?): Job {
        val generation = ++loadGeneration
        _selectedGamePackage.value = gamePackage
        _conversation.value = emptyList()
        val revisionAtLoadStart = ++conversationRevision

        loadJob?.cancel()
        val job = ownerScope.launch(ioDispatcher) {
            val loaded = runCatching {
                memory.warmUp()
                memory.recentConversationLines(
                    limit = maxHistory,
                    scope = scopeFor(gamePackage)
                )
            }.getOrDefault(emptyList())

            withContext(publicationDispatcher) {
                if (
                    generation == loadGeneration &&
                    conversationRevision == revisionAtLoadStart &&
                    _selectedGamePackage.value == gamePackage
                ) {
                    _conversation.value = loaded
                }
            }
        }
        loadJob = job
        return job
    }

    fun updateConversation(next: List<String>) {
        val previous = _conversation.value
        _conversation.value = next
        conversationRevision += 1

        val scope = scopeFor(_selectedGamePackage.value)
        if (next.isEmpty()) {
            memory.enqueueClearConversationHistory(scope)
            return
        }

        if (UltraMemoryTurnPersistencePolicy.shouldPersist(previous, next)) {
            memory.enqueueSyncConversation(
                previous = previous,
                next = next,
                scope = scope,
                timestampMillis = nowMillis()
            )
        }
    }

    private fun scopeFor(gamePackage: String?): UltraMemoryScope =
        UltraMemoryScope(
            userId = LOCAL_USER_ID,
            gamePackage = gamePackage
        )

    private companion object {
        const val LOCAL_USER_ID = "local"
    }
}
