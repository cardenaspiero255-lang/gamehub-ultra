package com.cardenaspiero255.gamehubultra.voice

internal data class UltraVoiceConversationDelta(
    val previous: List<String>,
    val next: List<String>
)

internal class UltraVoiceConversationLedger(
    private val maxEntries: Int = 8
) {
    private val entries = ArrayDeque<String>()
    private var boundGamePackage: String? = null
    private var scopeBound: Boolean = false
    private var observedPrivacyEpoch: Long = UltraPrivacyEpoch.current()

    private fun refreshPrivacyBoundary() {
        val now = UltraPrivacyEpoch.current()
        if (observedPrivacyEpoch != now) {
            entries.clear()
            observedPrivacyEpoch = now
        }
    }

    init { require(maxEntries >= 2) }

    fun bindScope(gamePackage: String?) {
        refreshPrivacyBoundary()
        if (!scopeBound || boundGamePackage != gamePackage) {
            entries.clear()
            boundGamePackage = gamePackage
            scopeBound = true
        }
    }

    fun snapshot(): List<String> {
        refreshPrivacyBoundary()
        return entries.toList()
    }

    fun record(userMessage: String, assistantMessage: String): UltraVoiceConversationDelta {
        refreshPrivacyBoundary()
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
