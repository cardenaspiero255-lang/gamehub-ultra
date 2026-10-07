package com.cardenaspiero255.gamehubultra.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ThermalPredictionAdvisorTest {
    @Test
    fun `actionable prediction becomes explicit estimated thermal action message`() {
        val message = assertNotNull(
            ThermalPredictionAdvisor.message(
                prediction = prediction(allowPreventiveSignal = true),
                previousObservation = null,
            )
        )

        assertEquals(SessionCoachSignal.THERMAL, message.signal)
        assertEquals(SessionCoachPriority.ACTION, message.priority)
        assertTrue(message.title.contains("previsto", ignoreCase = true))
        assertTrue(message.detail.contains("estimación", ignoreCase = true))
        assertTrue(message.detail.contains("no es una lectura", ignoreCase = true))
        assertTrue(message.action.orEmpty().contains("perfil", ignoreCase = true))
    }

    @Test
    fun `non actionable prediction does not create preventive message`() {
        assertNull(
            ThermalPredictionAdvisor.message(
                prediction = prediction(allowPreventiveSignal = false),
                previousObservation = null,
            )
        )
    }

    @Test
    fun `same active thermal prediction is not emitted repeatedly`() {
        val first = assertNotNull(
            ThermalPredictionAdvisor.message(
                prediction = prediction(allowPreventiveSignal = true),
                previousObservation = null,
            )
        )

        assertNull(
            ThermalPredictionAdvisor.message(
                prediction = prediction(allowPreventiveSignal = true),
                previousObservation = first,
            )
        )
    }

    private fun prediction(allowPreventiveSignal: Boolean) =
        ThermalPrediction(
            trend = ThermalTrend.RISING_FAST,
            risk = ThermalRisk.HIGH,
            confidence = 0.91f,
            signalMode = ThermalSignalMode.HEADROOM_AND_STATUS,
            slopePerMinute = 0.20f,
            accelerationPerMinuteSquared = 0.05f,
            latestMeasuredHeadroom = 0.70f,
            projectedHeadroom = 0.84f,
            allowPreventiveSignal = allowPreventiveSignal,
            recovering = false,
            evidence = emptyList(),
            reason = "Tendencia térmica ascendente."
        )
}
