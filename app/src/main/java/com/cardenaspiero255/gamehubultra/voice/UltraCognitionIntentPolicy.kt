package com.cardenaspiero255.gamehubultra.voice

/**
 * Phase 3 cognition policy: an utterance about an action is not authorization
 * to perform that action. Keep this pure and fail-closed for matched phrases.
 *
 * The caller routes these to conversation/research, never to the action engine.
 * This policy does not attempt unrestricted natural-language understanding.
 */
internal object UltraCognitionIntentPolicy {
    private val negatedCommand = Regex(
        """^(?:no|nunca|jamas|do not|dont|never)\s+(?:me\s+)?(?:actives?|activas?|activar|habilites?|habilita|habilitar|pongas?|pon|poner|apliques?|aplica|aplicar|abras?|abre|abrir|lances?|lanza|lanzar|inicies?|inicia|iniciar|open|launch|start|enable|activate|set)\b"""
    )

    private val hypotheticalStart = Regex(
        """^(?:que pasa si|que pasaria si|y si|what happens if|what would happen if)\b"""
    )

    private val howToCommand = Regex(
        """^(?:como|how (?:do i|can i|to|would i))\s+(?:(?:se|puedo|podria)\s+)?(?:activa|activar|habilita|habilitar|pon|pongo|poner|aplica|aplicar|abre|abro|abrir|open|launch|start|enable|activate|set)\b"""
    )

    private val recommendationRequest = Regex(
        """^(?:deberia|me conviene|es recomendable|should i|is it safe to)\b"""
    )

    private val quoteExplanation = Regex(
        """^(?:que significa|que hace|what does|what means)\s+(?:(?:el|la|the)\s+)?(?:comando|orden|command)\b"""
    )

    fun isNonExecutableDiscussion(transcript: String): Boolean {
        val normalized = VoiceCommandParser.stripLeadingAssistantInvocation(transcript)
        if (normalized.isBlank()) return false
        return negatedCommand.containsMatchIn(normalized) ||
            hypotheticalStart.containsMatchIn(normalized) ||
            howToCommand.containsMatchIn(normalized) ||
            recommendationRequest.containsMatchIn(normalized) ||
            quoteExplanation.containsMatchIn(normalized)
    }
}
