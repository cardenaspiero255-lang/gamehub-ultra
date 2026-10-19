package com.cardenaspiero255.gamehubultra.voice

import java.util.concurrent.atomic.AtomicLong

/**
 * Generation token belongs to the recognition request, not its eventual callback.
 * Cancellation and privacy erasure make previously queued transcripts unusable.
 */
internal class UltraRecognitionPrivacyGate {
    private val generation = AtomicLong()

    data class Token(val generation: Long, val epoch: Long)

    fun start(): Token = Token(
        generation = generation.incrementAndGet(),
        epoch = UltraPrivacyEpoch.current()
    )

    fun stop() {
        generation.incrementAndGet()
    }

    fun isCurrent(token: Token): Boolean =
        generation.get() == token.generation && UltraPrivacyEpoch.isCurrent(token.epoch)
}
