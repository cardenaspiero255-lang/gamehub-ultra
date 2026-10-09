package com.cardenaspiero255.gamehubultra.voice

import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * OMEGA concrete implementation checks for the finite contract:
 * .github/sentinel-contracts/voice-retry.omega.json
 *
 * This verifies real VoiceRecognitionRetryGate behavior, not an abstract model alone.
 * It does not prove Android vendor callbacks, no crashes or 100ms SLA.
 */
class VoiceRecognitionRetryGateContractTest {

    @Test
    fun exhaustiveEightStepSequencesMatchSpecifiedModel() {
        // 4^7 = 16,384 traces with a terminal CLOSED state.
        var traces = 0
        for (encoding in 0 until 16384) {
            val gate = VoiceRecognitionRetryGate()
            var pending = false
            var closed = false
            var cursor = encoding
            repeat(7) { step ->
                when (val operation = cursor % 4) {
                    0 -> {
                        val accepted = !pending && !closed
                        assertEquals(
                            accepted,
                            gate.trySchedule(),
                            "Trace $encoding step $step: unexpected schedule"
                        )
                        if (accepted) pending = true
                    }
                    1 -> {
                        gate.onRetryDispatched()
                        if (!closed) pending = false
                    }
                    2 -> {
                        gate.reset()
                        if (!closed) pending = false
                    }
                    3 -> {
                        gate.close()
                        closed = true
                        pending = false
                    }
                    else -> error("Unexpected operation $operation")
                }
                cursor /= 4
            }
            traces++
        }
        assertEquals(16384, traces)
    }

    @Test
    fun simultaneousScheduleRequestsAcceptOnlyOne() {
        val gate = VoiceRecognitionRetryGate()
        val workers = 8
        val pool = Executors.newFixedThreadPool(workers)
        val ready = CountDownLatch(workers)
        val start = CountDownLatch(1)
        try {
            val futures = (0 until workers).map {
                pool.submit(Callable {
                    ready.countDown()
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw AssertionError("Workers not released")
                    }
                    gate.trySchedule()
                })
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS), "Workers did not initialize")
            start.countDown()
            val accepted = futures.count { it.get(10, TimeUnit.SECONDS) }
            assertEquals(1, accepted, "Concurrent retries must be serialized")
            assertFalse(gate.trySchedule(), "Pending retry must still block duplicates")
            gate.onRetryDispatched()
            assertTrue(gate.trySchedule(), "Completion must permit another retry")
        } finally {
            start.countDown()
            pool.shutdownNow()
        }
    }

    @Test
    fun repeatedResetAlwaysReopensExactlyOneRetrySlot() {
        val gate = VoiceRecognitionRetryGate()
        repeat(500) {
            gate.reset()
            gate.reset()
            assertTrue(gate.trySchedule())
            assertFalse(gate.trySchedule())
        }
    }

    @Test
    fun closeIsIrreversibleEvenWhenDelayedCallbacksArrive() {
        val gate = VoiceRecognitionRetryGate()
        assertTrue(gate.trySchedule())
        gate.close()
        repeat(1000) {
            assertFalse(gate.trySchedule(), "Closed controller must never rearm retry")
            gate.onRetryDispatched()
            gate.reset()
        }
        assertFalse(gate.trySchedule())
    }

}
