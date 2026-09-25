package com.cardenaspiero255.gamehubultra.voice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UltraWakeSessionPolicyTest {
    @Test
    fun android13PlusPrefersPersistentSegmentedCapture() {
        val policy = UltraWakeSessionPolicy(sdkInt = 33)

        assertEquals(
            UltraWakeRecognitionMode.PERSISTENT_SEGMENTED,
            policy.preferredMode()
        )
    }

    @Test
    fun olderAndroidUsesLegacyRestartingRecognition() {
        val policy = UltraWakeSessionPolicy(sdkInt = 32)

        assertEquals(
            UltraWakeRecognitionMode.LEGACY_RESTARTING,
            policy.preferredMode()
        )
    }

    @Test
    fun persistentFailureFallsBackForRestOfServiceLifetime() {
        val policy = UltraWakeSessionPolicy(sdkInt = 36)

        policy.onPersistentSessionFailure()

        assertEquals(
            UltraWakeRecognitionMode.LEGACY_RESTARTING,
            policy.preferredMode()
        )
    }

    @Test
    fun commandQueueKeepsRapidFollowUpCommandsInOrder() {
        val queue = UltraWakeCommandQueue(capacity = 3)

        queue.offer("Ultra abre Immortal")
        queue.offer("Ultra activa X4")

        assertEquals("Ultra abre Immortal", queue.poll())
        assertEquals("Ultra activa X4", queue.poll())
        assertNull(queue.poll())
    }

    @Test
    fun commandQueueIsBoundedAndDropsOldestOverflow() {
        val queue = UltraWakeCommandQueue(capacity = 2)

        queue.offer("uno")
        queue.offer("dos")
        queue.offer("tres")

        assertEquals("dos", queue.poll())
        assertEquals("tres", queue.poll())
        assertNull(queue.poll())
    }
}
