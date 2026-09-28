package com.cardenaspiero255.gamehubultra.ai

import com.cardenaspiero255.gamehubultra.voice.NaturalLanguageIntentResolver

internal interface UltraAssistantGateway : AutoCloseable {
    fun hasLocalModelProvider(): Boolean

    fun isLocalModelAvailable(): Boolean

    fun generalKnowledgeChatOrNull(
        message: String,
        context: GameHubAiContext,
        conversation: List<String> = emptyList()
    ): String?

    fun advise(
        question: String,
        context: GameHubAiContext
    ): GameHubAiAdvice

    fun chat(
        message: String,
        context: GameHubAiContext,
        conversation: List<String> = emptyList()
    ): String

    fun intentResolver(): NaturalLanguageIntentResolver

    override fun close()
}
