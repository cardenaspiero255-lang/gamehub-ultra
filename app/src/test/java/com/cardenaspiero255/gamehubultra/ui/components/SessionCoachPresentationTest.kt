package com.cardenaspiero255.gamehubultra.ui.components

import com.cardenaspiero255.gamehubultra.domain.SessionCoachMessage
import com.cardenaspiero255.gamehubultra.domain.SessionCoachPostSessionReport
import com.cardenaspiero255.gamehubultra.domain.SessionCoachPriority
import com.cardenaspiero255.gamehubultra.domain.SessionCoachSignal
import com.cardenaspiero255.gamehubultra.domain.SessionCoachSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SessionCoachPresentationTest {
    @Test
    fun activeSessionShowsSampleCountAndOnlyLatestThreeObservations() {
        val observations = (1..4).map { index ->
            message("Aviso $index", "Detalle $index", "Acción $index")
        }

        val lines = SessionCoachPresentation.lines(
            preSession = null,
            liveSamples = listOf(snapshot()),
            observations = observations,
            postSession = null,
            sessionActive = true
        )

        assertTrue(lines.first().contains("1"))
        assertTrue(lines.none { it.contains("Aviso 1") })
        assertTrue(lines.any { it.contains("Aviso 4") })
        assertTrue(lines.any { it.contains("Acción 4") })
    }

    @Test
    fun idleSessionShowsPreSessionAndPostSessionNextSteps() {
        val post = SessionCoachPostSessionReport(
            summary = "Sesión analizada",
            nextSteps = listOf("Paso 1", "Paso 2", "Paso 3", "Paso 4"),
            patterns = emptyList(),
            batteryDropPercent = 10
        )

        val lines = SessionCoachPresentation.lines(
            preSession = message("Preparación 90/100", "Listo", null),
            liveSamples = emptyList(),
            observations = emptyList(),
            postSession = post,
            sessionActive = false
        )

        assertEquals("Preparación 90/100", lines.first())
        assertTrue(lines.contains("Listo"))
        assertTrue(lines.contains("Sesión analizada"))
        assertTrue(lines.contains("• Paso 3"))
        assertTrue(lines.none { it.contains("Paso 4") })
    }

    @Test
    fun activeSessionWithoutObservationsShowsStableState() {
        val lines = SessionCoachPresentation.lines(
            preSession = null,
            liveSamples = emptyList(),
            observations = emptyList(),
            postSession = null,
            sessionActive = true
        )

        assertEquals(listOf("Sesión activa · 0 muestras", "Sin cambios relevantes."), lines)
    }


    @Test
    fun idleSessionShowsLatestObservationInsteadOfDroppingIt() {
        val observation = message(
            "Cambio térmico relevante",
            "La presión térmica aumentó.",
            "Usa un perfil menos exigente."
        )

        val lines = SessionCoachPresentation.lines(
            preSession = null,
            liveSamples = emptyList(),
            observations = listOf(observation),
            postSession = null,
            sessionActive = false
        )

        assertTrue(lines.any { it.contains("Cambio térmico relevante") })
        assertTrue(lines.any { it.contains("perfil menos exigente") })
    }

    @Test
    fun presentationUsesCallerLocalizedLiveAndStableLabels() {
        val lines = SessionCoachPresentation.lines(
            preSession = null,
            liveSamples = emptyList(),
            observations = emptyList(),
            postSession = null,
            sessionActive = true,
            activeSessionText = "Active session · 0 samples analyzed",
            stableText = "No meaningful changes."
        )

        assertEquals(
            listOf(
                "Active session · 0 samples analyzed",
                "No meaningful changes."
            ),
            lines
        )
    }

    private fun message(title: String, detail: String, action: String?) =
        SessionCoachMessage(
            signal = SessionCoachSignal.GENERAL,
            priority = SessionCoachPriority.INFO,
            title = title,
            detail = detail,
            action = action
        )

    private fun snapshot() =
        SessionCoachSnapshot(
            timestampMillis = 1L,
            batteryPercent = 80,
            thermalStatus = 1,
            thermalHeadroom = 0.2f,
            refreshRateHz = 120f,
            latencyMs = 30L
        )
}
