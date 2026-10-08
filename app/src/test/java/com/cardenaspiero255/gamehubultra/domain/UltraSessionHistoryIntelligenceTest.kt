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
}
