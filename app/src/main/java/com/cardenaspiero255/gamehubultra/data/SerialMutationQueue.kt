package com.cardenaspiero255.gamehubultra.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Serializes suspend mutations in the exact order in which they are submitted.
 *
 * Each mutation waits for the previously submitted job to finish before running.
 * The parent scope owns cancellation, so callers can bind the queue to their lifecycle.
 */
class SerialMutationQueue(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val monitor = Any()
    private var tail: Job? = null

    fun enqueue(block: suspend () -> Unit): Job {
        val job = synchronized(monitor) {
            val previous = tail
            scope.launch(dispatcher, start = CoroutineStart.LAZY) {
                previous?.join()
                block()
            }.also { tail = it }
        }
        job.start()
        return job
    }

    suspend fun awaitIdle() {
        while (true) {
            val snapshot = synchronized(monitor) { tail } ?: return
            snapshot.join()
            if (synchronized(monitor) { tail === snapshot }) {
                return
            }
        }
    }
}
