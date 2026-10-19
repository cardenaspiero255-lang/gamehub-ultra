package com.cardenaspiero255.gamehubultra

import com.cardenaspiero255.gamehubultra.ai.UltraConversationPolicy
import com.cardenaspiero255.gamehubultra.voice.UltraPrivacyEpoch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal sealed interface UltraAssistantQuerySubmission {
    data class Accepted(val job: Job) : UltraAssistantQuerySubmission
    data object Rejected : UltraAssistantQuerySubmission
}

/**
 * Owns typed Ultra queries at the screen level so removing the assistant card
 * from composition does not cancel a submitted request or publish into stale UI
 * state.
 */
internal class UltraAssistantQueryRunner(
    private val ownerScope: CoroutineScope,
    private val executionDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val publicationDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val currentGamePackage: () -> String?,
    private val currentConversation: () -> List<String>,
    private val publishConversation: (List<String>) -> Unit
) {
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    private var activeJob: Job? = null
    private var activePrivacyEpoch: Long? = null

    /** Cancel an in-flight turn before a privacy erasure; later completions stay suppressed. */
    fun cancelActive() {
        synchronized(this) { activeJob?.cancel(CancellationException("Private context erased")) }
    }

    fun launch(
        onAccepted: () -> Unit,
        onFailure: suspend (Throwable) -> Unit,
        expectedPrivacyEpoch: Long? = null,
        block: suspend CoroutineScope.() -> Unit
    ): UltraAssistantQuerySubmission {
        val originatingGamePackage = currentGamePackage()
        // OCR-originated drafts MUST bring the epoch captured at user consent.
        // Ordinary typed messages may use the current epoch as before.
        val requestPrivacyEpoch = expectedPrivacyEpoch ?: UltraPrivacyEpoch.current()
        val accepted = synchronized(this) {
            if (_isRunning.value || !ownerScope.isActive || !UltraPrivacyEpoch.isCurrent(requestPrivacyEpoch)) {
                false
            } else {
                _isRunning.value = true
                activePrivacyEpoch = requestPrivacyEpoch
                true
            }
        }
        if (!accepted) return UltraAssistantQuerySubmission.Rejected

        val job = ownerScope.launch(executionDispatcher) {
            try {
                withContext(publicationDispatcher) {
                    if (currentGamePackage() != originatingGamePackage ||
                        !UltraPrivacyEpoch.isCurrent(requestPrivacyEpoch)) {
                        throw CancellationException("Selected game changed before query acceptance")
                    }
                    onAccepted()
                }
                // The acceptance callback may itself race an external erase.
                // Never dispatch a query under an epoch that has expired.
                if (!UltraPrivacyEpoch.isCurrent(requestPrivacyEpoch)) {
                    throw CancellationException("Private query authorization revoked before dispatch")
                }
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (UltraPrivacyEpoch.isCurrent(requestPrivacyEpoch)) onFailure(error)
            }
        }
        // Completion also runs for jobs cancelled before their body starts.
        synchronized(this) { activeJob = job }
        job.invokeOnCompletion {
            synchronized(this) {
                if (activeJob === job) {
                    activeJob = null
                    activePrivacyEpoch = null
                    _isRunning.value = false
                }
            }
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
            val requestEpoch = synchronized(this@UltraAssistantQueryRunner) { activePrivacyEpoch }
            if (currentGamePackage() != originatingGamePackage ||
                requestEpoch == null || !UltraPrivacyEpoch.isCurrent(requestEpoch) ||
                !currentCoroutineContext().isActive) {
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
