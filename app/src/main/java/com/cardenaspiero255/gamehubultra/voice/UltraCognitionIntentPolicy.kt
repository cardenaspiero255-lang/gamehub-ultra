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
        """^(?:(?:por favor|please)\s+)?(?:no|nunca|jamas|do not|dont|don t|never)\s+(?:me\s+)?(?:actives?|activas?|activar|habilites?|habilita|habilitar|pongas?|pon|poner|apliques?|aplica|aplicar|abras?|abre|abrir|lances?|lanza|lanzar|inicies?|inicia|iniciar|desactives?|desactiva|desactivar|deshabilites?|deshabilita|deshabilitar|quites?|quita|quitar|apagues?|apaga|apagar|disable|deactivate|turn off|switch off|open|launch|start|enable|activate|set)\b"""
    )

    private val indirectRefusal = Regex(
        """^(?:(?:por favor|please)\s+)?(?:no (?:quiero|quisiera|deseo|me gustaria) (?:que|activar|abrir|poner|habilitar|iniciar|lanzar)|(?:prefiero|preferiria) que no|no (?:debes|deberias)|evita(?: que(?: se)?)?|i (?:do not|dont|don t) want (?:you to|to))\b"""
    )
    private val explanationRequest = Regex(
        """^(?:quiero saber (?:si|como)|quisiera saber como|dime como|explicame como|me explicas como|(?:puedes|podrias) (?:explicar|decirme|mostrarme) como|(?:can|could) you (?:explain|tell me) how)\b"""
    )
    private val informationalDiscussion = Regex(
        """^(?:que (?:es|era|ventajas tiene)|es seguro|no creo que|quiero saber sobre|explicame|como funciona|dime informacion|para que sirve|cual es|no me interesa)\b"""
    )
    // ASR removes punctuation: an ambiguous question is not action authorization.
    private val ambiguousActionQuestion = Regex(
        """^(?:(?:por favor|please)\s+)?(?:puedes|podrias|can you|could you|would you)\s+(?:(?:me|please)\s+)?(?:activar|activa|habilitar|habilita|poner|pon|abrir|abre|lanzar|lanza|iniciar|inicia|desactiva|desactivar|deshabilita|deshabilitar|quita|quitar|apaga|apagar|disable|deactivate|turn off|switch off|open|launch|start|enable|activate|set)\b"""
    )

    private val hypotheticalStart = Regex(
        """^(?:que pasa si|que pasaria si|que ocurre si|y si|what happens if|what would happen if)\b"""
    )

    private val howToCommand = Regex(
        """^(?:como|how (?:do i|can i|to|would i))\s+(?:(?:se|puedo|podria)\s+)?(?:activa|activar|habilita|habilitar|pon|pongo|poner|aplica|aplicar|abre|abro|abrir|desactiva|desactivar|deshabilita|deshabilitar|quita|quitar|apaga|apagar|disable|deactivate|turn off|switch off|open|launch|start|enable|activate|set)\b"""
    )

    private val recommendationRequest = Regex(
        """^(?:deberia|seria bueno|me conviene|es recomendable|should i|is it safe to)\b"""
    )

    private val quoteExplanation = Regex(
        """^(?:que significa|que hace|what does|what means)\s+(?:(?:el|la|the)\s+)?(?:comando|orden|command)\b"""
    )

    fun isNonExecutableDiscussion(transcript: String): Boolean {
        val normalized = VoiceCommandParser.stripLeadingAssistantInvocation(transcript)
        if (normalized.isBlank()) return false
        return negatedCommand.containsMatchIn(normalized) ||
            indirectRefusal.containsMatchIn(normalized) ||
            explanationRequest.containsMatchIn(normalized) ||
            informationalDiscussion.containsMatchIn(normalized) ||
            ambiguousActionQuestion.containsMatchIn(normalized) ||
            hypotheticalStart.containsMatchIn(normalized) ||
            howToCommand.containsMatchIn(normalized) ||
            recommendationRequest.containsMatchIn(normalized) ||
            quoteExplanation.containsMatchIn(normalized)
    }
}
