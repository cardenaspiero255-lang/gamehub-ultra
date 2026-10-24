package com.cardenaspiero255.gamehubultra.voice

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Process-wide revocation fence shared by UI, in-flight callbacks and wake service. */
internal object UltraPrivacyEpoch {
    private val epoch = AtomicLong()
    private val observed = MutableStateFlow(0L)
    /** Observer used by privacy-sensitive UI to discard results immediately. */
    val changes: StateFlow<Long> = observed.asStateFlow()

    fun current(): Long = epoch.get()

    /** Any answer begun before this call must not republish its captured transcript. */
    fun invalidate(): Long {
        val next = epoch.incrementAndGet()
        // Monotonic update: simultaneous erasures cannot publish an older epoch.
        observed.update { previous -> maxOf(previous, next) }
        return next
    }

    fun isCurrent(captured: Long): Boolean = epoch.get() == captured
}
