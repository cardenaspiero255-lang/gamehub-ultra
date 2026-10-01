package com.cardenaspiero255.gamehubultra

import com.cardenaspiero255.gamehubultra.ai.UltraConversationScopePolicy

/** Prevents an asynchronous voice result from leaking into a newly selected game. */
object UltraVoiceResultPublisher {
    fun publishIfCurrentGame(
        originatingGamePackage: String?,
        currentGamePackage: String?,
        publish: () -> Unit
    ): Boolean =
        UltraConversationScopePolicy.runIfSameGame(
            originatingGamePackage = originatingGamePackage,
            currentGamePackage = currentGamePackage,
            publish = publish
        )
}
