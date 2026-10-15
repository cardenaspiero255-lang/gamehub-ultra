package com.cardenaspiero255.gamehubultra.voice

/**
 * Android TTS rejects oversized inputs. Split long spoken answers (such as
 * sourced phone comparisons) into complete, bounded utterances without
 * truncation. Use conservative chunks below platform speech input limits.
 */
internal object UltraSpeechChunker {
    const val DEFAULT_CHUNK_CHARS = 800

    fun chunk(text: String, maxChars: Int = DEFAULT_CHUNK_CHARS): List<String> {
        require(maxChars >= 32) { "TTS chunk size must be at least 32 characters" }
        var remaining = text.trim()
        if (remaining.isEmpty()) return emptyList()

        val chunks = mutableListOf<String>()
        while (remaining.length > maxChars) {
            val minimumBoundary = maxChars / 3
            val sentenceBoundary = (minimumBoundary..maxChars).lastOrNull { index ->
                remaining[index - 1] in ".!?;" && remaining[index].isWhitespace()
            }
            val wordBoundary = (minimumBoundary..maxChars).lastOrNull { index ->
                remaining[index].isWhitespace()
            }
            val boundary = sentenceBoundary ?: wordBoundary ?: maxChars
            chunks += remaining.substring(0, boundary).trimEnd()
            remaining = remaining.substring(boundary).trimStart()
        }
        if (remaining.isNotBlank()) chunks += remaining
        return chunks
    }
}
