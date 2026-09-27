package com.cardenaspiero255.gamehubultra.ai

/**
 * Plans a general Ultra query without letting old conversation text override
 * an explicit intent in the current turn.
 *
 * Explicit current-data/comparison intent in the current message wins. Only
 * genuinely ambiguous follow-ups inherit the previous user turn.
 */
object UltraContextualQueryPlanner {
    fun plan(
        message: String,
        conversationHistory: List<String> = emptyList()
    ): UltraGeneralQueryRequest {
        val direct = UltraGeneralQueryRouter.classify(message)
        if (!UltraConversationContextResolver.looksLikeFollowUp(message)) {
            return direct
        }

        val contextualText = UltraConversationContextResolver.resolve(
            message = message,
            conversation = conversationHistory
        )
        if (contextualText == message.trim()) {
            return direct
        }

        return when (direct.kind) {
            UltraGeneralQueryKind.CURRENT_DATA,
            UltraGeneralQueryKind.COMPARISON_RESEARCH ->
                direct.copy(originalText = contextualText)

            UltraGeneralQueryKind.GENERAL_KNOWLEDGE ->
                if (direct.requiresInternet) {
                    direct.copy(originalText = contextualText)
                } else {
                    UltraGeneralQueryRouter.classify(contextualText)
                        .copy(originalText = contextualText)
                }
        }
    }
}
