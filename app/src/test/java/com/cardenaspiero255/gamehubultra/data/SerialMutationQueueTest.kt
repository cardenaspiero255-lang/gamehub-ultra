package com.cardenaspiero255.gamehubultra.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

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
}
