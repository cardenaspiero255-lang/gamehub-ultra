package com.cardenaspiero255.gamehubultra

import com.cardenaspiero255.gamehubultra.voice.UltraPrivacyEpoch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UltraPhase5VoiceTurnCancellationTest {
    @Test
    fun privacyErasureCancelsPendingTypedAnswerWithoutRePublishing() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val started = CompletableDeferred<Unit>()
        val terminated = CompletableDeferred<Unit>()
        var published = false
        val runner = UltraAssistantQueryRunner(
            ownerScope = scope,
            executionDispatcher = Dispatchers.Unconfined,
            publicationDispatcher = Dispatchers.Unconfined,
            currentGamePackage = { "game.a" },
            currentConversation = { emptyList() },
            publishConversation = { published = true }
        )
        try {
            val accepted = assertIs<UltraAssistantQuerySubmission.Accepted>(
                runner.launch(onAccepted = {}, onFailure = {}) {
                    try {
                        started.complete(Unit)
                        awaitCancellation()
                    } finally {
                        terminated.complete(Unit)
                    }
                }
            )
            started.await()
            val captured = UltraPrivacyEpoch.current()
            UltraPrivacyEpoch.invalidate()
            runner.cancelActive()
            accepted.job.join()
            terminated.await()
            assertFalse(published)
            assertFalse(runner.isRunning.value)
            assertFalse(UltraPrivacyEpoch.isCurrent(captured))
            val second = assertIs<UltraAssistantQuerySubmission.Accepted>(
                runner.launch(onAccepted = {}, onFailure = {}) {}
            )
            second.job.join()
            assertTrue(second.job.isCompleted)
        } finally {
            scope.cancel()
        }
    }
}
