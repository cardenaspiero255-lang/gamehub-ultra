package com.cardenaspiero255.gamehubultra

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GameHubAiProfileMutationLaunchTest {
    private fun scope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @Test
    fun notifiesUIOnlyAfterSuccessfulDurableMutation() = runBlocking {
        val operationStarted = CompletableDeferred<Unit>()
        val allowCompletion = CompletableDeferred<Unit>()
        var updates = 0
        val job = launchAiProfileMutation(scope(), "apply", mutation = {
            operationStarted.complete(Unit)
            allowCompletion.await()
            true
        }, onChanged = { updates++ })
        operationStarted.await()
        assertEquals(0, updates)
        allowCompletion.complete(Unit)
        job.join()
        assertEquals(1, updates)
        assertTrue(job.isCompleted)
    }

    @Test
    fun rejectedVersionDoesNotRefreshUI() = runBlocking {
        var updates = 0
        val job = launchAiProfileMutation(scope(), "apply", mutation = { false }) {
            updates++
        }
        job.join()
        assertEquals(0, updates)
        assertFalse(job.isCancelled)
    }

    @Test
    fun caughtPersistenceFailureDoesNotRefreshUI() = runBlocking {
        var updates = 0
        val job = launchAiProfileMutation(scope(), "rollback", mutation = {
            error("deliberate DataStore failure")
        }) { updates++ }
        job.join()
        assertEquals(0, updates)
        assertFalse(job.isCancelled)
    }

    @Test
    fun cancellationIsPropagatedAndDoesNotRefreshUI() = runBlocking {
        var updates = 0
        val job = launchAiProfileMutation(scope(), "apply", mutation = {
            throw CancellationException("screen left")
        }) { updates++ }
        job.join()
        assertEquals(0, updates)
        assertTrue(job.isCancelled)
    }
}
