package com.cardenaspiero255.gamehubultra

import com.cardenaspiero255.gamehubultra.ai.UltraMemoryScope
import com.cardenaspiero255.gamehubultra.ai.UltraMemoryTurnPersistencePolicy
import com.cardenaspiero255.gamehubultra.data.UltraConversationMemoryStore
import kotlinx.coroutines.CancellationException
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
 * Minimal memory boundary for Ultra's live assistant session.
 *
 * The controller depends on this contract instead of Android persistence so
 * game switching, history recovery and persistence can be tested directly.
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
 * Single owner for Ultra's live conversation session.
 *
 * The controller keeps the selected-game scope, visible conversation,
 * persistence policy and query runner together. History loads are generation
 * and revision guarded so a stale game/load can never overwrite newer state.
 */
internal class UltraAssistantSessionController(
    private val ownerScope: CoroutineScope,
    private val memory: UltraAssistantSessionMemory,
    private val maxHistory: Int,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val publicationDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    restoredGamePackage: String? = null,
    restoredConversation: List<String>? = null,
    private val onSnapshotChanged: (String?, List<String>) -> Unit = { _, _ -> }
) {
    private val _conversation = MutableStateFlow(restoredConversation.orEmpty())
    val conversation: StateFlow<List<String>> = _conversation.asStateFlow()

    private val _selectedGamePackage = MutableStateFlow(
        if (restoredConversation != null) restoredGamePackage else null
    )
    val selectedGamePackage: StateFlow<String?> = _selectedGamePackage.asStateFlow()

    private val _loadError = MutableStateFlow<Throwable?>(null)
    val loadError: StateFlow<Throwable?> = _loadError.asStateFlow()

    private var scopeInitialized = restoredConversation != null
    private var restoredSnapshotPending = restoredConversation != null
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
        if (
            restoredSnapshotPending &&
            _selectedGamePackage.value == gamePackage
        ) {
            restoredSnapshotPending = false
            scopeInitialized = true
            _loadError.value = null
            return noOpJob()
        }

        restoredSnapshotPending = false
        if (
            scopeInitialized &&
            _selectedGamePackage.value == gamePackage
        ) {
            return noOpJob()
        }

        scopeInitialized = true
        return startLoad(
            gamePackage = gamePackage,
            clearConversation = true
        )
    }

    fun retryLoad(): Job =
        startLoad(
            gamePackage = _selectedGamePackage.value,
            clearConversation = false
        )

    fun updateConversation(next: List<String>) {
        val previous = _conversation.value
        if (previous == next) return

        _conversation.value = next
        conversationRevision += 1
        onSnapshotChanged(_selectedGamePackage.value, next)

        val memoryScope = scopeFor(_selectedGamePackage.value)
        if (next.isEmpty()) {
            memory.enqueueClearConversationHistory(memoryScope)
            return
        }

        if (UltraMemoryTurnPersistencePolicy.shouldPersist(previous, next)) {
            memory.enqueueSyncConversation(
                previous = previous,
                next = next,
                scope = memoryScope,
                timestampMillis = nowMillis()
            )
        }
    }

    private fun startLoad(
        gamePackage: String?,
        clearConversation: Boolean
    ): Job {
        val generation = ++loadGeneration
        _selectedGamePackage.value = gamePackage
        if (clearConversation) {
            _conversation.value = emptyList()
            conversationRevision += 1
        }
        val revisionAtLoadStart = conversationRevision
        _loadError.value = null

        loadJob?.cancel()
        return ownerScope.launch(ioDispatcher) {
            try {
                memory.warmUp()
                val loaded = memory.recentConversationLines(
                    limit = maxHistory,
                    scope = scopeFor(gamePackage)
                )

                withContext(publicationDispatcher) {
                    if (
                        generation == loadGeneration &&
                        _selectedGamePackage.value == gamePackage
                    ) {
                        if (conversationRevision == revisionAtLoadStart) {
                            _conversation.value = loaded
                            conversationRevision += 1
                            onSnapshotChanged(gamePackage, loaded)
                        }
                        _loadError.value = null
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                withContext(publicationDispatcher) {
                    if (
                        generation == loadGeneration &&
                        _selectedGamePackage.value == gamePackage
                    ) {
                        _loadError.value = error
                    }
                }
            }
        }.also { loadJob = it }
    }

    private fun noOpJob(): Job =
        ownerScope.launch(publicationDispatcher) { }

    private fun scopeFor(gamePackage: String?): UltraMemoryScope =
        UltraMemoryScope(
            userId = LOCAL_USER_ID,
            gamePackage = gamePackage
        )

    private companion object {
        const val LOCAL_USER_ID = "local"
    }
}
