package com.cardenaspiero255.gamehubultra.ai

/** Keeps asynchronous assistant results bound to the game that originated them. */
internal object UltraConversationScopePolicy {
    /** Returns true when the result still belongs to the currently selected game. */
    fun isSameGame(
        originatingGamePackage: String?,
        currentGamePackage: String?
    ): Boolean = originatingGamePackage == currentGamePackage

    /**
     * Publishes a turn result only while the selected game still matches the
     * game that originated the asynchronous request.
     */
    fun runIfSameGame(
        originatingGamePackage: String?,
        currentGamePackage: String?,
        publish: () -> Unit
    ): Boolean {
        if (!isSameGame(originatingGamePackage, currentGamePackage)) {
            return false
        }
        publish()
        return true
    }
}
