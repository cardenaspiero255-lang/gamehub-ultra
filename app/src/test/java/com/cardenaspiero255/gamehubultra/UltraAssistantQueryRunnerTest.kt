package com.cardenaspiero255.gamehubultra

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class UltraAssistantQueryRunnerTest {
    @Test
    fun submittedQuerySurvivesVoiceCardScopeCancellation() = runBlocking {
        val ownerScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val cardScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val completed = CompletableDeferred<Unit>()

        try {
            val runner = UltraAssistantQueryRunner(ownerScope)
            val job = runner.launch {
                started.complete(Unit)
                release.await()
                completed.complete(Unit)
            }

            started.await()
            cardScope.cancel()
            release.complete(Unit)
            job.join()

            assertTrue(completed.isCompleted)
        } finally {
            cardScope.cancel()
            ownerScope.cancel()
        }
    }
}
