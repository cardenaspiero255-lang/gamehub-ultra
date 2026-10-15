package com.cardenaspiero255.gamehubultra.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UltraSessionHistoryIntelligenceTest {
    private fun session(
        day: Int,
        game: String = "com.example.cod",
        refresh: Int? = 90,
        battery: Int? = 15,
        night: Boolean = true,
        thermal: Int? = 3,
        count: Int = 8
    ) = UltraRecordedGameSession(
        packageName = game,
        startedAtMillis = day * 86_400_000L,
        endedAtMillis = day * 86_400_000L + 900_000L,
        measuredSampleCount = count,
        typicalRefreshRateHz = refresh,
        minimumBatteryPercent = battery,
        maximumThermalStatus = thermal,
        playedAtNight = night
    )

    @Test
    fun recallsOnlyMeasuredPreviousGameAndNeverInventsCelsius() {
        val history = listOf(
            session(1, game = "com.other", refresh = 120),
            session(2, thermal = 4, refresh = 120)
        )
        val answer = UltraSessionHistoryIntelligence.lastSessionSummary(
            history, "com.example.cod"
        )!!
        assertTrue(answer.contains("120 Hz"))
        assertTrue(answer.contains("estado térmico 4"))
        assertTrue(!answer.contains("°C"))
        assertEquals(null, UltraSessionHistoryIntelligence.lastSessionSummary(history, "missing"))
    }

    @Test
    fun ordinaryHealthHabitsQuestionIsNotHijackedByGameProfileHistory() {
        val answer = UltraSessionHistoryIntelligence.response(
            query = "¿Qué hábitos ayudan a dormir mejor?",
            packageName = "com.example.cod",
            history = emptyList()
        )
        assertNull(answer)
    }

    @Test
    fun gameHabitsQuestionCanSuggestNightProfile() {
        val answer = UltraSessionHistoryIntelligence.response(
            query = "¿Qué hábitos de mis sesiones de juego sirven para un perfil Noche?",
            packageName = "com.example.cod",
            history = listOf(session(1), session(2), session(3))
        )
        assertTrue(answer.orEmpty().contains("Noche"))
    }

    @Test
    fun onlyThreeDistinctMeasuredNightSessionsTriggerSuggestion() {
        val history = listOf(session(1), session(2), session(3))
        val suggestion = UltraSessionHistoryIntelligence.proposeNightProfile(
            history, "com.example.cod"
        )
        assertTrue(suggestion!!.contains("Noche"))
        assertTrue(suggestion.contains("¿Quieres"))
        assertTrue(!suggestion.contains("activado"))
        assertNull(UltraSessionHistoryIntelligence.proposeNightProfile(history.take(2), "com.example.cod"))
        assertNull(UltraSessionHistoryIntelligence.proposeNightProfile(
            history.map { it.copy(typicalRefreshRateHz = null) }, "com.example.cod"
        ))
        assertNull(UltraSessionHistoryIntelligence.proposeNightProfile(
            history.map { it.copy(minimumBatteryPercent = 80) }, "com.example.cod"
        ))
    }

    @Test
    fun invalidMeasurementsAndMissingHistoryNeverFabricateVerifiedSessionFacts() {
        assertNull(UltraSessionHistoryIntelligence.fromMeasuredSamples(
            packageName = "com.example.cod",
            startedAtMillis = 2_000L,
            endedAtMillis = 1_000L,
            samples = emptyList()
        ))

        val recordedWithoutMetrics = session(
            day = 5, refresh = null, battery = null, thermal = null
        )
        val summary = UltraSessionHistoryIntelligence.lastSessionSummary(
            listOf(recordedWithoutMetrics), "com.example.cod"
        )
        assertTrue(summary.orEmpty().contains("no métricas verificables"))

        val missing = UltraSessionHistoryIntelligence.response(
            query = "¿Cómo fue mi última sesión?",
            packageName = "com.example.cod",
            history = emptyList()
        )
        assertEquals("No tengo una sesión anterior medida de este juego.", missing)

        val unqualified = UltraSessionHistoryIntelligence.response(
            query = "¿Me propones un perfil Noche?",
            packageName = "com.example.cod",
            history = emptyList()
        )
        assertEquals(
            "Todavía no tengo suficientes sesiones medidas para proponerte un perfil Noche.",
            unqualified
        )
    }


    @Test
    fun thirdPartyGamingQuestionsCannotExposePrivateSessionHistory() {
        val history = listOf(session(day = 4, refresh = 120))
        for (query in listOf(
            "¿Cuándo fue la última vez que jugó Messi?",
            "¿Cómo fue la última sesión de Messi?",
            "¿Cuál fue la última partida de otro jugador?"
        )) {
            assertNull(
                UltraSessionHistoryIntelligence.response(
                    query, "com.example.cod", history
                ), "Unrelated third-party history cannot reveal local metrics: $query"
            )
        }
        for (query in listOf(
            "¿Cómo fue mi última sesión?",
            "¿Cuándo fue la última vez que jugué?",
            "¿Cómo estuvo mi última partida?"
        )) {
            assertTrue(
                UltraSessionHistoryIntelligence.response(
                    query, "com.example.cod", history
                ).orEmpty().contains("120 Hz"),
                "Explicit first-person gaming history must remain available: $query"
            )
        }
    }

}
