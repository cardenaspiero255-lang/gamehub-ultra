package com.cardenaspiero255.gamehubultra.voice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UltraSpeechChunkerTest {
    @Test
    fun splitsDetailedPhoneComparisonIntoBoundedSpeechWithoutLosingWords() {
        val original = (1..80).joinToString(" ") { index ->
            "El teléfono $index tiene una batería distinta y su rendimiento requiere pruebas."
        }
        val chunks = UltraSpeechChunker.chunk(original, maxChars = 150)

        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.isNotBlank() && it.length <= 150 })
        assertEquals(original, chunks.joinToString(" "))
    }

    @Test
    fun preservesShortResponsesAndDoesNotSpeakBlankInput() {
        assertEquals(listOf("Hola, ¿qué quieres jugar?"),
            UltraSpeechChunker.chunk("  Hola, ¿qué quieres jugar?  "))
        assertTrue(UltraSpeechChunker.chunk("   ").isEmpty())
    }

    @Test
    fun handlesLongUnbrokenTokensWithoutInfiniteLoopsOrMissingCharacters() {
        val word = "x".repeat(230)
        val chunks = UltraSpeechChunker.chunk(word, maxChars = 64)
        assertTrue(chunks.all { it.length in 1..64 })
        assertEquals(word, chunks.joinToString(""))
    }

    @Test
    fun prefersSentenceBoundariesWhenThereIsRoom() {
        val chunks = UltraSpeechChunker.chunk(
            "Primero, batería y carga. Segundo, pantalla y procesador. " +
                "Tercero, refrigeración y juegos.",
            maxChars = 45
        )
        assertEquals("Primero, batería y carga.", chunks.first())
        assertTrue(chunks.all { it.length <= 45 })
    }
}
