package com.cardenaspiero255.gamehubultra.voice

import kotlin.test.Test
import kotlin.test.assertEquals

class UltraSpeechCandidateRankerTest {

    @Test
    fun higherConfidenceAlternativeCanBeatFirstRecognition() {
        val result = UltraSpeechCandidateRanker.select(
            alternatives = listOf(
                "qué es un montón",
                "qué es un motor"
            ),
            confidenceScores = floatArrayOf(0.31f, 0.88f)
        )

        assertEquals("qué es un motor", result)
    }

    @Test
    fun wakeWordCandidateKeepsPriorityWhenConfidenceIsUsable() {
        val result = UltraSpeechCandidateRanker.select(
            alternatives = listOf(
                "qué es un motor",
                "Ultra qué es un motor"
            ),
            confidenceScores = floatArrayOf(0.91f, 0.84f),
            prefer = UltraWakeWordMatcher::contains
        )

        assertEquals("Ultra qué es un motor", result)
    }

    @Test
    fun wakeCommandBeatsHigherConfidenceWakeOnlyAlternative() {
        val result = UltraSpeechCandidateRanker.select(
            alternatives = listOf(
                "Ultra",
                "Ultra abre Steam"
            ),
            confidenceScores = floatArrayOf(0.96f, 0.74f),
            prefer = UltraWakeWordMatcher::contains
        )

        assertEquals("Ultra abre Steam", result)
    }

    @Test
    fun blankAndInvalidConfidenceCandidatesAreIgnoredSafely() {
        val result = UltraSpeechCandidateRanker.select(
            alternatives = listOf("", "   ", "Ultra abre RE4"),
            confidenceScores = floatArrayOf(Float.NaN, -1f, 0.73f)
        )

        assertEquals("Ultra abre RE4", result)
    }
}
