package com.cardenaspiero255.gamehubultra.voice

import java.util.concurrent.atomic.AtomicLong

/** Process-wide revocation fence shared by UI, in-flight callbacks and wake service. */
internal object UltraPrivacyEpoch {
    private val epoch = AtomicLong()

    fun current(): Long = epoch.get()

    /** Any answer begun before this call must not republish its captured transcript. */
    fun invalidate(): Long = epoch.incrementAndGet()

    fun isCurrent(captured: Long): Boolean = epoch.get() == captured
}
