package com.cardenaspiero255.gamehubultra.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

/**
 * Process-lifetime queue for durability-sensitive selection/profile writes initiated by voice.
 *
 * Voice services can be destroyed immediately after accepting a command. Keeping this queue
 * independent from a service lifecycle lets already accepted DataStore mutations finish while
 * SerialMutationQueue preserves submission order.
 */
internal object DurableSelectionMutationQueue {
    private val queue = SerialMutationQueue(
        scope = CoroutineScope(SupervisorJob())
    )

    fun enqueue(block: suspend () -> Unit): Job =
        queue.enqueue(block)
}
