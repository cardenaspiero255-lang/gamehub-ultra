package com.cardenaspiero255.gamehubultra

import com.cardenaspiero255.gamehubultra.voice.UltraPrivacyEpoch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UltraAssistantQueryRunnerTest {
    @Test
    fun staleOCRDraftIsRejectedAtRunnerAdmissionAfterExternalErase() = runBlocking {
        val originalEpoch = UltraPrivacyEpoch.current()
        UltraPrivacyEpoch.invalidate()
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var acceptedTurns = 0
            var executed = false
            val runner = UltraAssistantQueryRunner(
                ownerScope = owner,
                executionDispatcher = Dispatchers.Unconfined,
                publicationDispatcher = Dispatchers.Unconfined,
                currentGamePackage = { "game.a" },
                currentConversation = { emptyList() },
                publishConversation = {}
            )
            val response = runner.launch(
                onAccepted = { acceptedTurns++ },
                onFailure = { error("Invalid OCR must not schedule a fallback") },
                expectedPrivacyEpoch = originalEpoch
            ) { executed = true }
            assertIs<UltraAssistantQuerySubmission.Rejected>(response)
            assertEquals(0, acceptedTurns)
            assertFalse(executed)
            assertFalse(runner.isRunning.value)
        } finally { owner.cancel() }
    }

    @Test
    fun erasureInAcceptanceCallbackCannotDispatchPreviouslyApprovedOCRQuery() = runBlocking {
        val approvedEpoch = UltraPrivacyEpoch.current()
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var executed = false
            val runner = UltraAssistantQueryRunner(
                ownerScope = owner,
                executionDispatcher = Dispatchers.Unconfined,
                publicationDispatcher = Dispatchers.Unconfined,
                currentGamePackage = { "game.a" },
                currentConversation = { emptyList() },
                publishConversation = {}
            )
            val response = runner.launch(
                expectedPrivacyEpoch = approvedEpoch,
                onAccepted = { UltraPrivacyEpoch.invalidate() },
                onFailure = { error("Cancelled OCR must not report a provider failure") }
            ) { executed = true }
            val submitted = assertIs<UltraAssistantQuerySubmission.Accepted>(response)
            submitted.job.join()
            assertFalse(executed)
            assertFalse(runner.isRunning.value)
        } finally { owner.cancel() }
    }

    @Test
    fun externalEraseDuringSuspendedRoutingPreventsOCRFromReachingRemoteProvider() = runBlocking {
        val original = UltraPrivacyEpoch.current()
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val routingStarted = CompletableDeferred<Unit>()
        val resumeRouting = CompletableDeferred<Unit>()
        var providerInvocations = 0
        try {
            val runner = UltraAssistantQueryRunner(
                ownerScope = owner,
                executionDispatcher = Dispatchers.Unconfined,
                publicationDispatcher = Dispatchers.Unconfined,
                currentGamePackage = { "game.a" },
                currentConversation = { emptyList() },
                publishConversation = {}
            )
            val submission = runner.launch(
                expectedPrivacyEpoch = original,
                onAccepted = {},
                onFailure = { error("Cancelled private query must not send an error") }
            ) {
                routingStarted.complete(Unit)
                resumeRouting.await() // Simulates an asynchronous agentRouter.route call.
                runner.requireCurrentDispatch("game.a") // Before queryExecutor/aiAdvisor.
                providerInvocations += 1
            }
            val accepted = assertIs<UltraAssistantQuerySubmission.Accepted>(submission)
            routingStarted.await()
            UltraPrivacyEpoch.invalidate()
            resumeRouting.complete(Unit)
            accepted.job.join()
            assertEquals(0, providerInvocations)
            assertFalse(runner.isRunning.value)
        } finally { owner.cancel() }
    }

    @Test
    fun consentStillCurrentAtProviderBoundaryAllowsApprovedRequest() = runBlocking {
        val original = UltraPrivacyEpoch.current()
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var calls = 0
        try {
            val runner = UltraAssistantQueryRunner(
                ownerScope = owner,
                executionDispatcher = Dispatchers.Unconfined,
                publicationDispatcher = Dispatchers.Unconfined,
                currentGamePackage = { "game.a" },
                currentConversation = { emptyList() },
                publishConversation = {}
            )
            val response = runner.launch(
                expectedPrivacyEpoch = original,
                onAccepted = {},
                onFailure = { error("No fallback expected") }
            ) {
                runner.requireCurrentDispatch("game.a")
                calls += 1
            }
            assertIs<UltraAssistantQuerySubmission.Accepted>(response).job.join()
            assertEquals(1, calls)
        } finally { owner.cancel() }
    }

    @Test
    fun submittedQuerySurvivesVoiceCardScopeCancellation() = runBlocking {
        val ownerScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val cardScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val completed = CompletableDeferred<Unit>()
        var currentGame: String? = "game.a"
        var conversation = emptyList<String>()

        try {
            val runner = UltraAssistantQueryRunner(
                ownerScope = ownerScope,
                executionDispatcher = Dispatchers.Unconfined,
                publicationDispatcher = Dispatchers.Unconfined,
                currentGamePackage = { currentGame },
                currentConversation = { conversation },
                publishConversation = { conversation = it }
            )
            val submission = runner.launch(
                onAccepted = {},
                onFailure = { error("failure callback must not run") }
            ) {
                started.complete(Unit)
                release.await()
                completed.complete(Unit)
            }
            val accepted = assertIs<UltraAssistantQuerySubmission.Accepted>(submission)

            started.await()
            assertTrue(runner.isRunning.value)

            cardScope.cancel()
            assertTrue(runner.isRunning.value)

            release.complete(Unit)
            accepted.job.join()

            assertTrue(completed.isCompleted)
            assertFalse(runner.isRunning.value)
        } finally {
            cardScope.cancel()
            ownerScope.cancel()
        }
    }

    @Test
    fun secondSubmissionIsRejectedBeforeItsUserTurnIsRecorded() = runBlocking {
        val ownerScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val release = CompletableDeferred<Unit>()
        var acceptedTurns = 0

        try {
            val runner = UltraAssistantQueryRunner(
                ownerScope = ownerScope,
                executionDispatcher = Dispatchers.Unconfined,
                publicationDispatcher = Dispatchers.Unconfined,
                currentGamePackage = { "game.a" },
                currentConversation = { emptyList() },
                publishConversation = {}
            )
            val first = runner.launch(
                onAccepted = { acceptedTurns += 1 },
                onFailure = {}
            ) {
                release.await()
            }
            val acceptedFirst = assertIs<UltraAssistantQuerySubmission.Accepted>(first)

            val second = runner.launch(
                onAccepted = { acceptedTurns += 1 },
                onFailure = {}
            ) {}

            assertIs<UltraAssistantQuerySubmission.Rejected>(second)
            assertEquals(1, acceptedTurns)

            release.complete(Unit)
            acceptedFirst.job.join()
        } finally {
            ownerScope.cancel()
        }
    }

    @Test
    fun queryFailureIsDeliveredWithoutEscapingTheRunner() = runBlocking {
        val ownerScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val failure = CompletableDeferred<Throwable>()

        try {
            val runner = UltraAssistantQueryRunner(
                ownerScope = ownerScope,
                executionDispatcher = Dispatchers.Unconfined,
                publicationDispatcher = Dispatchers.Unconfined,
                currentGamePackage = { "game.a" },
                currentConversation = { emptyList() },
                publishConversation = {}
            )
            val submission = runner.launch(
                onAccepted = {},
                onFailure = { failure.complete(it) }
            ) {
                error("boom")
            }
            val accepted = assertIs<UltraAssistantQuerySubmission.Accepted>(submission)
            accepted.job.join()

            assertEquals("boom", failure.await().message)
            assertFalse(runner.isRunning.value)
        } finally {
            ownerScope.cancel()
        }
    }

    @Test
    fun completionUsesScreenOwnedCurrentGameAfterCardDisposal() = runBlocking {
        val ownerScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val release = CompletableDeferred<Unit>()
        var currentGame: String? = "game.a"
        var conversation = listOf("Tú: pregunta")
        var published = false

        try {
            val runner = UltraAssistantQueryRunner(
                ownerScope = ownerScope,
                executionDispatcher = Dispatchers.Unconfined,
                publicationDispatcher = Dispatchers.Unconfined,
                currentGamePackage = { currentGame },
                currentConversation = { conversation },
                publishConversation = {
                    published = true
                    conversation = it
                }
            )
            val submission = runner.launch(
                onAccepted = {},
                onFailure = {}
            ) {
                release.await()
                runner.appendAssistantIfCurrentGame(
                    originatingGamePackage = "game.a",
                    assistantEntry = "Ultra: respuesta antigua",
                    maxEntries = 8
                )
            }
            val accepted = assertIs<UltraAssistantQuerySubmission.Accepted>(submission)

            currentGame = "game.b"
            release.complete(Unit)
            accepted.job.join()

            assertFalse(published)
            assertEquals(listOf("Tú: pregunta"), conversation)
        } finally {
            ownerScope.cancel()
        }
    }
    @Test
    fun acceptedCallbackFailureReleasesRunnerForNextSubmission() = runBlocking {
        val ownerScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

        try {
            val runner = UltraAssistantQueryRunner(
                ownerScope = ownerScope,
                executionDispatcher = Dispatchers.Unconfined,
                publicationDispatcher = Dispatchers.Unconfined,
                currentGamePackage = { "game.a" },
                currentConversation = { emptyList() },
                publishConversation = {}
            )

            val failure = CompletableDeferred<Throwable>()
            val submission = runner.launch(
                onAccepted = { error("accept failed") },
                onFailure = { failure.complete(it) }
            ) {}
            val failedSubmission = assertIs<UltraAssistantQuerySubmission.Accepted>(submission)
            failedSubmission.job.join()

            val acceptanceFailure = failure.await()
            assertIs<IllegalStateException>(acceptanceFailure)
            assertEquals("accept failed", acceptanceFailure.message)
            assertFalse(runner.isRunning.value)

            val retry = runner.launch(
                onAccepted = {},
                onFailure = {}
            ) {}
            val accepted = assertIs<UltraAssistantQuerySubmission.Accepted>(retry)
            accepted.job.join()
        } finally {
            ownerScope.cancel()
        }
    }

    @Test
    fun assistantAppendCanResetConversationForCurrentGame() = runBlocking {
        val ownerScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var conversation = listOf("Tú: vieja", "Ultra: vieja")

        try {
            val runner = UltraAssistantQueryRunner(
                ownerScope = ownerScope,
                executionDispatcher = Dispatchers.Unconfined,
                publicationDispatcher = Dispatchers.Unconfined,
                currentGamePackage = { "game.a" },
                currentConversation = { conversation },
                publishConversation = { conversation = it }
            )

            var published = false
            val submitted = assertIs<UltraAssistantQuerySubmission.Accepted>(
                runner.launch(onAccepted = {}, onFailure = {}) {
                    published = runner.appendAssistantIfCurrentGame(
                        originatingGamePackage = "game.a",
                        assistantEntry = "  Ultra: nueva  ",
                        maxEntries = 8,
                        resetConversation = true
                    )
                }
            )
            submitted.job.join()
            assertTrue(published)
            assertEquals(listOf("Ultra: nueva"), conversation)
        } finally {
            ownerScope.cancel()
        }
    }

    @Test
    fun assistantAppendKeepsConversationBounded() = runBlocking {
        val ownerScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var conversation = listOf("uno", "dos", "tres")

        try {
            val runner = UltraAssistantQueryRunner(
                ownerScope = ownerScope,
                executionDispatcher = Dispatchers.Unconfined,
                publicationDispatcher = Dispatchers.Unconfined,
                currentGamePackage = { "game.a" },
                currentConversation = { conversation },
                publishConversation = { conversation = it }
            )

            val submitted = assertIs<UltraAssistantQuerySubmission.Accepted>(
                runner.launch(onAccepted = {}, onFailure = {}) {
                    runner.appendAssistantIfCurrentGame(
                        originatingGamePackage = "game.a",
                        assistantEntry = "cuatro",
                        maxEntries = 3
                    )
                }
            )
            submitted.job.join()
            assertEquals(listOf("dos", "tres", "cuatro"), conversation)
        } finally {
            ownerScope.cancel()
        }
    }

    @Test
    fun cancelledOwnerScopeRejectsSubmissionBeforeRecordingUserTurn() = runBlocking {
        val ownerScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var acceptedTurns = 0

        ownerScope.cancel()

        val runner = UltraAssistantQueryRunner(
            ownerScope = ownerScope,
            publicationDispatcher = Dispatchers.Unconfined,
            currentGamePackage = { "game.a" },
            currentConversation = { emptyList() },
            publishConversation = {}
        )

        val submission = runner.launch(
            onAccepted = { acceptedTurns += 1 },
            onFailure = {}
        ) {}

        assertIs<UltraAssistantQuerySubmission.Rejected>(submission)
        assertEquals(0, acceptedTurns)
        assertFalse(runner.isRunning.value)
    }


    @Test
    fun cancellingAcceptedQueryDoesNotReportFailureAndReleasesRunner() = runBlocking {
        val ownerScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val started = CompletableDeferred<Unit>()
        var failures = 0

        try {
            val runner = UltraAssistantQueryRunner(
                ownerScope = ownerScope,
                executionDispatcher = Dispatchers.Unconfined,
                publicationDispatcher = Dispatchers.Unconfined,
                currentGamePackage = { "game.a" },
                currentConversation = { emptyList() },
                publishConversation = {}
            )

            val submission = runner.launch(
                onAccepted = {},
                onFailure = { failures += 1 }
            ) {
                started.complete(Unit)
                awaitCancellation()
            }
            val accepted = assertIs<UltraAssistantQuerySubmission.Accepted>(submission)

            started.await()
            assertTrue(runner.isRunning.value)

            accepted.job.cancel()
            accepted.job.join()

            assertEquals(0, failures)
            assertFalse(runner.isRunning.value)
        } finally {
            ownerScope.cancel()
        }
    }


    @Test
    fun cancellationBeforeScheduledExecutionDoesNotRecordAcceptedTurn() = runBlocking {
        val executionDispatcher = QueuedTestDispatcher()
        val ownerScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var acceptedTurns = 0
        var executed = false

        val runner = UltraAssistantQueryRunner(
            ownerScope = ownerScope,
            executionDispatcher = executionDispatcher,
            publicationDispatcher = Dispatchers.Unconfined,
            currentGamePackage = { "game.a" },
            currentConversation = { emptyList() },
            publishConversation = {}
        )

        val submission = runner.launch(
            onAccepted = { acceptedTurns += 1 },
            onFailure = {}
        ) {
            executed = true
        }
        val accepted = assertIs<UltraAssistantQuerySubmission.Accepted>(submission)

        ownerScope.cancel()
        executionDispatcher.runAll()
        accepted.job.join()

        assertEquals(0, acceptedTurns)
        assertFalse(executed)
        assertFalse(runner.isRunning.value)
    }


    @Test
    fun gameChangeBeforeDeferredAcceptanceDoesNotRecordTurn() = runBlocking {
        val publicationDispatcher = QueuedTestDispatcher()
        val ownerScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var currentGame: String? = "game.a"
        var acceptedTurns = 0
        var executed = false

        try {
            val runner = UltraAssistantQueryRunner(
                ownerScope = ownerScope,
                executionDispatcher = Dispatchers.Unconfined,
                publicationDispatcher = publicationDispatcher,
                currentGamePackage = { currentGame },
                currentConversation = { emptyList() },
                publishConversation = {}
            )

            val submission = runner.launch(
                onAccepted = { acceptedTurns += 1 },
                onFailure = {}
            ) {
                executed = true
            }
            val accepted = assertIs<UltraAssistantQuerySubmission.Accepted>(submission)

            currentGame = "game.b"
            publicationDispatcher.runAll()
            accepted.job.join()

            assertEquals(0, acceptedTurns)
            assertFalse(executed)
            assertFalse(runner.isRunning.value)
        } finally {
            ownerScope.cancel()
        }
    }

    @Test
    fun gameChangeBeforeScheduledExecutionDoesNotRecordAcceptedTurn() = runBlocking {
        val executionDispatcher = QueuedTestDispatcher()
        val ownerScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var currentGame: String? = "game.a"
        var acceptedTurns = 0
        var executed = false

        val runner = UltraAssistantQueryRunner(
            ownerScope = ownerScope,
            executionDispatcher = executionDispatcher,
            publicationDispatcher = Dispatchers.Unconfined,
            currentGamePackage = { currentGame },
            currentConversation = { emptyList() },
            publishConversation = {}
        )

        val submission = runner.launch(
            onAccepted = { acceptedTurns += 1 },
            onFailure = {}
        ) {
            executed = true
        }
        val accepted = assertIs<UltraAssistantQuerySubmission.Accepted>(submission)

        currentGame = "game.b"
        executionDispatcher.runAll()
        accepted.job.join()

        assertEquals(0, acceptedTurns)
        assertFalse(executed)
        assertFalse(runner.isRunning.value)
    }

    @Test
    fun deferredAcceptanceForSameGameRecordsTurnAndExecutesQuery() = runBlocking {
        val publicationDispatcher = QueuedTestDispatcher()
        val ownerScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var acceptedTurns = 0
        var executed = false

        try {
            val runner = UltraAssistantQueryRunner(
                ownerScope = ownerScope,
                executionDispatcher = Dispatchers.Unconfined,
                publicationDispatcher = publicationDispatcher,
                currentGamePackage = { "game.a" },
                currentConversation = { emptyList() },
                publishConversation = {}
            )

            val submission = runner.launch(
                onAccepted = { acceptedTurns += 1 },
                onFailure = {}
            ) {
                executed = true
            }
            val accepted = assertIs<UltraAssistantQuerySubmission.Accepted>(submission)

            assertEquals(0, acceptedTurns)
            assertFalse(executed)

            publicationDispatcher.runAll()
            accepted.job.join()

            assertEquals(1, acceptedTurns)
            assertTrue(executed)
            assertFalse(runner.isRunning.value)
        } finally {
            ownerScope.cancel()
        }
    }

    @Test
    fun scheduledExecutionForSameGameRecordsTurnAndExecutesQuery() = runBlocking {
        val executionDispatcher = QueuedTestDispatcher()
        val ownerScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var acceptedTurns = 0
        var executed = false

        try {
            val runner = UltraAssistantQueryRunner(
                ownerScope = ownerScope,
                executionDispatcher = executionDispatcher,
                publicationDispatcher = Dispatchers.Unconfined,
                currentGamePackage = { "game.a" },
                currentConversation = { emptyList() },
                publishConversation = {}
            )

            val submission = runner.launch(
                onAccepted = { acceptedTurns += 1 },
                onFailure = {}
            ) {
                executed = true
            }
            val accepted = assertIs<UltraAssistantQuerySubmission.Accepted>(submission)

            assertEquals(0, acceptedTurns)
            assertFalse(executed)

            executionDispatcher.runAll()
            accepted.job.join()

            assertEquals(1, acceptedTurns)
            assertTrue(executed)
            assertFalse(runner.isRunning.value)
        } finally {
            ownerScope.cancel()
        }
    }

}

private class QueuedTestDispatcher : CoroutineDispatcher() {
    private val tasks = mutableListOf<Runnable>()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        tasks += block
    }

    fun runAll() {
        val queued = tasks.toList()
        tasks.clear()
        queued.forEach(Runnable::run)
    }
}
