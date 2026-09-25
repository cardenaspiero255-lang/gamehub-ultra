package com.cardenaspiero255.gamehubultra.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SerialMutationQueueTest {
    @Test
    fun runsMutationsStrictlyInSubmissionOrder() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val queue = SerialMutationQueue(scope)
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()

        try {
            queue.enqueue {
                synchronized(events) { events += "first-start" }
                firstStarted.complete(Unit)
                releaseFirst.await()
                synchronized(events) { events += "first-end" }
            }

            queue.enqueue {
                synchronized(events) { events += "second" }
                secondStarted.complete(Unit)
            }

            firstStarted.await()
            assertFalse(secondStarted.isCompleted)

            releaseFirst.complete(Unit)
            queue.awaitIdle()

            assertEquals(
                listOf("first-start", "first-end", "second"),
                synchronized(events) { events.toList() }
            )
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun failedMutationIsContainedAndJobStillReportsFailure() = runBlocking {
        val uncaught = CompletableDeferred<Throwable>()
        val parentHandler = CoroutineExceptionHandler { _, throwable ->
            uncaught.complete(throwable)
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + parentHandler)
        val queue = SerialMutationQueue(scope)
        val nextRan = CompletableDeferred<Unit>()

        try {
            val failed = queue.enqueue {
                error("simulated datastore failure")
            }
            queue.enqueue {
                nextRan.complete(Unit)
            }

            queue.awaitIdle()

            assertTrue(failed.isCancelled)
            assertTrue(nextRan.isCompleted)
            assertFalse(uncaught.isCompleted)
        } finally {
            scope.cancel()
        }
    }

}
