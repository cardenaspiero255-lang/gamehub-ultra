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

internal class UltraAssistantSessionRetryGate(
    private val maxRetriesPerScope: Int
) {
    private var initialized = false
    private var currentGamePackage: String? = null
    private var retriesConsumed = 0

    fun consumeRetry(
        gamePackage: String?,
        hasLoadError: Boolean
    ): Boolean {
        if (!initialized || currentGamePackage != gamePackage) {
            initialized = true
            currentGamePackage = gamePackage
            retriesConsumed = 0
        }
        if (!hasLoadError || retriesConsumed >= maxRetriesPerScope) {
            return false
        }
        retriesConsumed += 1
        return true
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
    restoredHistoryHydrated: Boolean? = null,
    private val onSnapshotChanged: (String?, List<String>) -> Unit = { _, _ -> },
    private val onHistoryHydrationChanged: (Boolean) -> Unit = {}
) {
    private var historyHydrated =
        restoredConversation != null && (restoredHistoryHydrated ?: true)

    private val _conversation = MutableStateFlow(restoredConversation.orEmpty())
    val conversation: StateFlow<List<String>> = _conversation.asStateFlow()

    private val _selectedGamePackage = MutableStateFlow(
        if (restoredConversation != null) restoredGamePackage else null
    )
    val selectedGamePackage: StateFlow<String?> = _selectedGamePackage.asStateFlow()

    private val _scopeReady = MutableStateFlow(
        restoredConversation != null && historyHydrated
    )
    val scopeReady: StateFlow<Boolean> = _scopeReady.asStateFlow()

    private val _loadError = MutableStateFlow<Throwable?>(null)
    val loadError: StateFlow<Throwable?> = _loadError.asStateFlow()

    private var scopeInitialized = restoredConversation != null
    private var restoredSnapshotPending = restoredConversation != null
    private var loadGeneration = 0L
    private var conversationRevision = 0L
    private var failedLoadRevision: Long? = null
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
            if (historyHydrated) {
                _scopeReady.value = true
                return noOpJob()
            }
            return startLoad(
                gamePackage = gamePackage,
                clearConversation = false,
                mergeExistingConversation = true
            )
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

    fun retryLoad(): Job {
        val failedRevision = failedLoadRevision ?: return noOpJob()
        return startLoad(
            gamePackage = _selectedGamePackage.value,
            clearConversation = false,
            expectedConversationRevision = failedRevision
        )
    }

    fun updateConversation(next: List<String>) {
        val previous = _conversation.value
        val memoryScope = scopeFor(_selectedGamePackage.value)
        if (previous == next) {
            if (next.isEmpty()) {
                conversationRevision += 1
                onSnapshotChanged(_selectedGamePackage.value, next)
                memory.enqueueClearConversationHistory(memoryScope)
            }
            return
        }

        _conversation.value = next
        conversationRevision += 1
        onSnapshotChanged(_selectedGamePackage.value, next)

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
        clearConversation: Boolean,
        expectedConversationRevision: Long? = null,
        mergeExistingConversation: Boolean = false
    ): Job {
        val generation = ++loadGeneration
        _selectedGamePackage.value = gamePackage
        historyHydrated = false
        onHistoryHydrationChanged(false)
        _scopeReady.value = false
        if (clearConversation) {
            _conversation.value = emptyList()
            conversationRevision += 1
            failedLoadRevision = null
            onSnapshotChanged(gamePackage, emptyList())
        }
        val revisionAtLoadStart =
            expectedConversationRevision ?: conversationRevision
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
                            val nextConversation =
                                if (mergeExistingConversation) {
                                    mergeConversationHistory(
                                        persisted = loaded,
                                        restored = _conversation.value
                                    )
                                } else {
                                    loaded.takeLast(maxHistory)
                                }
                            _conversation.value = nextConversation
                            conversationRevision += 1
                            historyHydrated = true
                            onSnapshotChanged(gamePackage, nextConversation)
                            onHistoryHydrationChanged(true)
                        }
                        failedLoadRevision = null
                        _loadError.value = null
                        _scopeReady.value = true
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
                        failedLoadRevision = revisionAtLoadStart
                        historyHydrated = false
                        onHistoryHydrationChanged(false)
                        _loadError.value = error
                        _scopeReady.value = true
                    }
                }
            }
        }.also { loadJob = it }
    }

    private fun mergeConversationHistory(
        persisted: List<String>,
        restored: List<String>
    ): List<String> {
        if (persisted.isEmpty()) {
            return restored.takeLast(maxHistory)
        }
        if (restored.isEmpty()) {
            return persisted.takeLast(maxHistory)
        }

        val maxOverlap = minOf(persisted.size, restored.size)
        val overlap = (maxOverlap downTo 1).firstOrNull { size ->
            persisted.takeLast(size) == restored.take(size)
        } ?: 0

        return (persisted + restored.drop(overlap)).takeLast(maxHistory)
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
