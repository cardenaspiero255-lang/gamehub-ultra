package com.cardenaspiero255.gamehubultra.ai

/** Stable UI boundary; the Android encrypted persistence is an implementation detail. */
interface UltraMemoryPrivacyGateway {
    fun isMemoryEnabled(): Boolean
    fun conversationRetentionDays(): Int
    fun setMemoryEnabled(enabled: Boolean)
    fun setConversationRetentionDays(days: Int): Boolean
    fun clearConversationHistory(scope: UltraMemoryScope)
    fun deleteAllPrivateMemory()
}

/** Fail-closed fallback for isolated previews and dependency construction. */
object UnavailableUltraMemoryPrivacyGateway : UltraMemoryPrivacyGateway {
    override fun isMemoryEnabled(): Boolean = false
    override fun conversationRetentionDays(): Int = 30
    override fun setMemoryEnabled(enabled: Boolean) {
        error("Privacy storage unavailable")
    }
    override fun setConversationRetentionDays(days: Int): Boolean = false
    override fun clearConversationHistory(scope: UltraMemoryScope) {
        error("Privacy storage unavailable")
    }
    override fun deleteAllPrivateMemory() {
        error("Privacy storage unavailable")
    }
}
