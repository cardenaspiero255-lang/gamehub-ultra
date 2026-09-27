package com.cardenaspiero255.gamehubultra

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UltraAssistantQueryRunnerTest {
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
            assertIs<UltraAssistantQuerySubmission.Accepted>(first)

            val second = runner.launch(
                onAccepted = { acceptedTurns += 1 },
                onFailure = {}
            ) {}

            assertIs<UltraAssistantQuerySubmission.Rejected>(second)
            assertEquals(1, acceptedTurns)

            release.complete(Unit)
            (first as UltraAssistantQuerySubmission.Accepted).job.join()
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
                publicationDispatcher = Dispatchers.Unconfined,
                currentGamePackage = { "game.a" },
                currentConversation = { emptyList() },
                publishConversation = {}
            )

            val failure = runCatching {
                runner.launch(
                    onAccepted = { error("accept failed") },
                    onFailure = {}
                ) {}
            }.exceptionOrNull()

            assertIs<IllegalStateException>(failure)
            assertEquals("accept failed", failure.message)
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
                publicationDispatcher = Dispatchers.Unconfined,
                currentGamePackage = { "game.a" },
                currentConversation = { conversation },
                publishConversation = { conversation = it }
            )

            val published = runner.appendAssistantIfCurrentGame(
                originatingGamePackage = "game.a",
                assistantEntry = "  Ultra: nueva  ",
                maxEntries = 8,
                resetConversation = true
            )

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
                publicationDispatcher = Dispatchers.Unconfined,
                currentGamePackage = { "game.a" },
                currentConversation = { conversation },
                publishConversation = { conversation = it }
            )

            runner.appendAssistantIfCurrentGame(
                originatingGamePackage = "game.a",
                assistantEntry = "cuatro",
                maxEntries = 3
            )

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


}
