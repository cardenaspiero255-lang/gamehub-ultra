package com.cardenaspiero255.gamehubultra.domain

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UltraGamingIntelligenceV3Test {
    private val game = "com.example.game"
    private val now = 1_000_000L

    private fun sample(
        ago: Long,
        refresh: Float? = 120f,
        battery: Int? = 60,
        thermal: Int? = 1,
        latency: Long? = 35L,
        charging: Boolean? = false
    ) = SessionCoachSnapshot(
        timestampMillis = now - ago,
        batteryPercent = battery,
        thermalStatus = thermal,
        thermalHeadroom = null,
        refreshRateHz = refresh,
        latencyMs = latency,
        batteryCharging = charging
    )

    private fun window(
        samples: List<SessionCoachSnapshot>,
        packageName: String = game,
        endedAt: Long? = null,
        startedAt: Long = now - 120_000L
    ) = UltraGamingMeasurementWindow(
        packageName = packageName,
        startedAtMillis = startedAt,
        endedAtMillis = endedAt,
        samples = samples
    )

    @Test
    fun genericKnowledgeAndThirdPartyQuestionsNeverExposePrivateTelemetry() {
        val current = window(listOf(sample(5_000), sample(10_000), sample(15_000)))
        for (question in listOf(
            "¿Qué significa FPS?",
            "¿Qué es la latencia de Internet?",
            "¿Cuántos FPS tiene el jugador Messi?",
            "¿Cómo está la batería del teléfono de otra persona?",
            "Compara los FPS de RedMagic 11S Pro y 12 Pro"
        )) {
            assertNull(UltraGamingIntelligenceV3.answer(question, game, current, now), question)
        }
    }

    @Test
    fun noSelectedGameOrActiveSessionIsFailClosed() {
        val question = "¿Cómo van los FPS de mi juego?"
        val a = UltraGamingIntelligenceV3.answer(question, null, null, now)
        assertContains(a.orEmpty(), "Selecciona un juego")
        val b = UltraGamingIntelligenceV3.answer(question, game, null, now)
        assertContains(b.orEmpty(), "sesión activa")
        val c = UltraGamingIntelligenceV3.answer(
            question, game, window(listOf(sample(5_000)), packageName = "com.other"), now
        )
        assertContains(c.orEmpty(), "sesión activa")
        val ended = UltraGamingIntelligenceV3.answer(
            question, game, window(listOf(sample(5_000)), endedAt = now - 1), now
        )
        assertContains(ended.orEmpty(), "sesión activa")
    }

    @Test
    fun onlyRecentValidMeasuredSamplesCanSupportAResult() {
        val current = window(listOf(
            sample(170_000), // outside both active window and freshness budget
            sample(10_000, refresh = Float.NaN, battery = -3, thermal = 99, latency = -10),
            sample(8_000),
            sample(8_000), // duplicate timestamp is not a separate sample
            sample(-1_000), // future samples cannot be trusted
            sample(6_000)
        ))
        val result = UltraGamingIntelligenceV3.answer(
            "¿Cómo va el rendimiento de mi juego?", game, current, now
        ).orEmpty()
        assertContains(result, "3 muestras")
        assertFalse(result.contains("99"))
        assertFalse(result.contains("NaN"))
        assertFalse(result.contains("-10"))
        assertContains(result, "FPS del juego no medidos")
    }

    @Test
    fun missingMetricsNeverInventsFpsPacingTemperatureOrGameServerPing() {
        val noSignals = window(listOf(
            sample(5_000, null, null, null, null),
            sample(10_000, null, null, null, null),
            sample(15_000, null, null, null, null)
        ))
        val answer = UltraGamingIntelligenceV3.answer(
            "¿Cómo van los FPS y el ping en mi juego?", game, noSignals, now
        ).orEmpty()
        assertContains(answer, "FPS del juego no medidos")
        assertContains(answer, "frame pacing no verificable")
        assertContains(answer, "sin latencia medida")
        assertFalse(answer.contains("°C"))
        assertFalse(answer.contains("60 FPS"))
    }

    @Test
    fun refreshRateIsReportedAsScreenHzAndNeverAsGameFps() {
        val current = window(listOf(sample(3_000), sample(7_000), sample(11_000), sample(14_000)))
        val answer = UltraGamingIntelligenceV3.answer(
            "¿Cuántos FPS tengo en mi juego?", game, current, now
        ).orEmpty()
        assertContains(answer, "120 Hz")
        assertTrue(answer.contains("refresco de pantalla observado", ignoreCase = true), answer)
        assertContains(answer, "FPS del juego no medidos")
        assertContains(answer, "frame pacing no verificable")
        assertFalse(answer.contains("120 FPS"))
    }

    @Test
    fun thermalAndBatteryRecommendationsRequireSustainedEvidenceAndNeverExecuteActions() {
        val sustained = window(listOf(
            sample(5_000, battery = 15, thermal = 4),
            sample(10_000, battery = 17, thermal = 4),
            sample(15_000, battery = 18, thermal = 3)
        ))
        val answer = UltraGamingIntelligenceV3.answer(
            "¿Qué recomiendas para mi juego por temperatura y batería?", game, sustained, now
        ).orEmpty()
        assertTrue(answer.contains("estado térmico Android", ignoreCase = true), answer)
        assertContains(answer, "presión térmica")
        assertContains(answer, "batería baja")
        assertContains(answer, "perfil equilibrado")
        assertContains(answer, "sin activar ajustes")
        assertFalse(answer.contains("°C"))
        val single = window(listOf(
            sample(5_000, battery = 50, thermal = 1),
            sample(10_000, battery = 50, thermal = 1),
            sample(15_000, battery = 15, thermal = 4)
        ))
        val otherAnswer = UltraGamingIntelligenceV3.answer(
            "¿Qué recomiendas para mi juego por temperatura?", game, single, now
        ).orEmpty()
        assertFalse(otherAnswer.contains("presión térmica"))
        assertFalse(otherAnswer.contains("batería baja"))
    }

    @Test
    fun networkAdviceUsesMeasuredDiagnosticsButNeverClaimsServerPing() {
        val current = window(listOf(
            sample(5_000, latency = 280),
            sample(10_000, latency = 260),
            sample(15_000, latency = 230),
            sample(20_000, latency = 20)
        ))
        val answer = UltraGamingIntelligenceV3.answer(
            "¿Cómo va la latencia en mi partida?", game, current, now
        ).orEmpty()
        assertTrue(answer.contains("latencia de diagnóstico", ignoreCase = true), answer)
        assertContains(answer, "ms")
        assertContains(answer, "no es ping al servidor")
        assertContains(answer, "latencia alta")
        assertContains(answer, "Wi-Fi")
    }

    @Test
    fun staleOrInsufficientSamplesCannotTriggerRecommendations() {
        val current = window(listOf(
            sample(200_000, battery = 2, thermal = 5, latency = 900),
            sample(5_000, battery = 10, thermal = 5, latency = 900)
        ), startedAt = now - 250_000)
        val answer = UltraGamingIntelligenceV3.answer(
            "Analiza mi partida y dame recomendaciones", game, current, now
        ).orEmpty()
        assertTrue(answer.contains("muestras recientes insuficientes", ignoreCase = true), answer)
        assertFalse(answer.contains("perfil equilibrado"))
        assertFalse(answer.contains("Wi-Fi"))
    }

    @Test
    fun explicitOtherGameCannotReadCurrentGameDiagnostics() {
        val current = window(listOf(sample(5_000), sample(8_000), sample(11_000)))
        val other = UltraGamingIntelligenceV3.answer(
            "¿Cuántos FPS tiene mi juego com.other.game?",
            game, current, now
        ).orEmpty()
        assertFalse(other.contains("120 Hz"), other)
        assertContains(other, "juego indicado")
    }

    @Test
    fun activePlayPhrasingWorksWithoutMyGameKeyword() {
        val current = window(listOf(sample(5_000), sample(8_000), sample(11_000)))
        val answer = UltraGamingIntelligenceV3.answer(
            "¿Cómo rinde el juego que estoy jugando?", game, current, now
        ).orEmpty()
        assertContains(answer, "FPS del juego no medidos")
        assertContains(answer, "120 Hz")
    }

    @Test
    fun invalidTimeBoundsCannotResurrectStaleOrUnrelatedSessions() {
        val active = window(
            samples = listOf(sample(4_000), sample(8_000), sample(12_000)),
            startedAt = now + 1_000
        )
        val answer = UltraGamingIntelligenceV3.answer(
            "¿Cómo va mi juego?", game, active, now
        ).orEmpty()
        assertContains(answer, "sesión activa")
    }
}
