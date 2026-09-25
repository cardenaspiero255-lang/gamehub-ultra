package com.cardenaspiero255.gamehubultra.voice

internal data class UltraVoiceConversationDelta(
    val previous: List<String>,
    val next: List<String>
)

internal class UltraVoiceConversationLedger(
    private val maxEntries: Int = 8
) {
    private val entries = ArrayDeque<String>()

    init { require(maxEntries >= 2) }

    fun snapshot(): List<String> = entries.toList()

    fun record(userMessage: String, assistantMessage: String): UltraVoiceConversationDelta {
        val previous = snapshot()
        append("Tú: " + userMessage.trim())
        append("Ultra: " + assistantMessage.trim())
        return UltraVoiceConversationDelta(previous, snapshot())
    }

    fun clear() { entries.clear() }

    private fun append(entry: String) {
        if (entry.substringAfter(':').isBlank()) return
        entries.addLast(entry)
        while (entries.size > maxEntries) entries.removeFirst()
    }
}
