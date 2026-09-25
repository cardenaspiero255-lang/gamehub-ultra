package com.cardenaspiero255.gamehubultra.ai

internal object UltraConversationScopePolicy {
    fun isSameGame(
        originatingGamePackage: String?,
        currentGamePackage: String?
    ): Boolean = originatingGamePackage == currentGamePackage
}
