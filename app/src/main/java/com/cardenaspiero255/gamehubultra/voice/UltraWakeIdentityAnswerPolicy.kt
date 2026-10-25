package com.cardenaspiero255.gamehubultra.voice

/**
 * Identity questions must be answered using locally persisted configuration,
 * not an LLM guess. No change to the wake word, voice capture or game profile
 * occurs when reading these answers.
 */
internal object UltraWakeIdentityAnswerPolicy {
    private const val ORIGINAL_NAME = UltraWakeNamePolicy.DEFAULT_NAME
    private val nameQuestions = setOf(
        "como te llamas",
        "cual es tu nombre",
        "que nombre tienes",
        "dime como te llamas",
        "dime cual es tu nombre"
    )
    private val reasonQuestions = Regex(
        """^(?:por que te llamas(?: [a-z]{2,20})?|por que tienes ese nombre|por que ese nombre)$"""
    )
    private val previousQuestions = setOf(
        "como te llamabas antes",
        "cual era tu nombre anterior",
        "como te llamabas originalmente"
    )

    /** Returns null unless the complete normalized utterance is a known identity question. */
    fun respond(
        transcript: String,
        currentName: String,
        previousName: String?
    ): String? {
        val question = VoiceCommandParser.stripLeadingAssistantInvocation(transcript)
        val current = UltraWakeNamePolicy.validate(currentName) ?: ORIGINAL_NAME
        val previous = previousName
            ?.let(UltraWakeNamePolicy::validate)
            ?.takeUnless { it == current }
        val displayName = current.displayName()
        val displayPrevious = previous?.displayName()

        return when {
            question in nameQuestions ->
                if (current == ORIGINAL_NAME) {
                    "Me llamo Ultra. Es mi nombre predeterminado."
                } else {
                    val history = if (displayPrevious != null) {
                        "Antes me llamaba $displayPrevious."
                    } else {
                        "Mi nombre original es Ultra."
                    }
                    "Me llamo $displayName, porque ese es el nombre que me pusiste. $history"
                }
            reasonQuestions.matches(question) ->
                if (current == ORIGINAL_NAME) {
                    "Me llamo Ultra porque es mi nombre predeterminado."
                } else {
                    "Me llamo $displayName porque ese es el nombre que me pusiste."
                }
            question in previousQuestions ->
                if (displayPrevious != null) {
                    "Antes me llamaba $displayPrevious. Ahora me llamo $displayName."
                } else if (current == ORIGINAL_NAME) {
                    "Mi nombre original es Ultra. No tengo registrado un nombre anterior."
                } else {
                    "Mi nombre original es Ultra. No tengo registrado el nombre inmediatamente anterior."
                }
            else -> null
        }
    }

    private fun String.displayName(): String = replaceFirstChar { it.uppercase() }
}
