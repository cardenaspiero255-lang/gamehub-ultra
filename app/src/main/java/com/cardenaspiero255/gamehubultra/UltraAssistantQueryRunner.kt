package com.cardenaspiero255.gamehubultra

import com.cardenaspiero255.gamehubultra.ai.UltraConversationPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface UltraAssistantQuerySubmission {
    data class Accepted(val job: Job) : UltraAssistantQuerySubmission
    data object Rejected : UltraAssistantQuerySubmission
}

/**
 * Owns typed Ultra queries at the screen level so removing the assistant card
 * from composition does not cancel a submitted request or publish into stale UI
 * state.
 */
class UltraAssistantQueryRunner(
    private val ownerScope: CoroutineScope,
    private val executionDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val publicationDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val currentGamePackage: () -> String?,
    private val currentConversation: () -> List<String>,
    private val publishConversation: (List<String>) -> Unit
) {
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    fun launch(
        onAccepted: () -> Unit,
        onFailure: suspend (Throwable) -> Unit,
        block: suspend CoroutineScope.() -> Unit
    ): UltraAssistantQuerySubmission {
        val originatingGamePackage = currentGamePackage()
        val accepted = synchronized(this) {
            if (_isRunning.value || !ownerScope.isActive) {
                false
            } else {
                _isRunning.value = true
                true
            }
        }
        if (!accepted) return UltraAssistantQuerySubmission.Rejected

        val job = ownerScope.launch(executionDispatcher) {
            try {
                withContext(publicationDispatcher) {
                    if (currentGamePackage() != originatingGamePackage) {
                        throw CancellationException("Selected game changed before query acceptance")
                    }
                    onAccepted()
                }
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                onFailure(error)
            }
        }
        // Completion also runs for jobs cancelled before their body starts.
        job.invokeOnCompletion {
            _isRunning.value = false
        }
        return UltraAssistantQuerySubmission.Accepted(job)
    }

    suspend fun appendAssistantIfCurrentGame(
        originatingGamePackage: String?,
        assistantEntry: String,
        maxEntries: Int,
        resetConversation: Boolean = false
    ): Boolean =
        withContext(publicationDispatcher) {
            if (currentGamePackage() != originatingGamePackage) {
                return@withContext false
            }

            val next =
                if (resetConversation) {
                    listOf(assistantEntry.trim()).filter(String::isNotBlank)
                } else {
                    UltraConversationPolicy.append(
                        history = currentConversation(),
                        entry = assistantEntry,
                        maxEntries = maxEntries
                    )
                }
            publishConversation(next)
            true
        }
}
