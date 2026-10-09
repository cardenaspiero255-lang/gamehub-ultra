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
        // 3^8 = 6,561 possible operation traces; a fresh gate for each trace.
        var traces = 0
        for (encoding in 0 until 6561) {
            val gate = VoiceRecognitionRetryGate()
            var expectedPending = false
            var cursor = encoding
            repeat(8) { step ->
                when (val operation = cursor % 3) {
                    0 -> {
                        val shouldSchedule = !expectedPending
                        assertEquals(
                            shouldSchedule,
                            gate.trySchedule(),
                            "Trace $encoding step $step: duplicate retry eligibility"
                        )
                        expectedPending = true
                    }
                    1 -> {
                        gate.onRetryDispatched()
                        expectedPending = false
                    }
                    2 -> {
                        gate.reset()
                        expectedPending = false
                    }
                    else -> error("Unexpected operation $operation")
                }
                cursor /= 3
            }
            traces++
        }
        assertEquals(6561, traces)
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
}
