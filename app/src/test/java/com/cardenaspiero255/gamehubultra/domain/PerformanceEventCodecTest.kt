package com.cardenaspiero255.gamehubultra.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PerformanceEventCodecTest {
    @Test
    fun roundTripPreservesEvent() {
        val event = PerformanceEvent(
            timestampMillis = 1_762_000_000_000,
            type = PerformanceEventType.POLICY_CHANGED,
            profile = PerformanceProfile.X4,
            score = 91,
            detail = "Térmica estable\ncon margen suficiente"
        )

        val decoded = PerformanceEventCodec.decode(PerformanceEventCodec.encode(event))

        assertEquals(event.timestampMillis, decoded?.timestampMillis)
        assertEquals(event.type, decoded?.type)
        assertEquals(event.profile, decoded?.profile)
        assertEquals(event.score, decoded?.score)
        assertEquals("Térmica estable con margen suficiente", decoded?.detail)
    }

    @Test
    fun invalidLinesAreIgnored() {
        assertNull(PerformanceEventCodec.decode(""))
        assertNull(PerformanceEventCodec.decode("1\tUNKNOWN\tX4\t80\treason"))
        assertNull(PerformanceEventCodec.decode("not-a-time\tSESSION_STARTED\t\t\tdetail"))
    }
}
