package com.cardenaspiero255.gamehubultra.voice

/**
 * Snapshot of the runtime state that may safely disambiguate short voice commands.
 * Null values mean the context is unavailable and must never be invented.
 */
internal data class UltraVoiceContext(
    val selectedGamePackage: String? = null,
    val thermalLabel: String? = null
)

internal object UltraContextualVoiceResolver {
    private val currentGameLaunch = Regex(
        """^(?:abre|abrir|abreme|lanza|lanzar|inicia|iniciar|ejecuta|ejecutar|juega|open|launch|start|run|play)\s+(?:este|this)\s+(?:juego|game)$"""
    )

    fun resolve(
        transcript: String,
        context: UltraVoiceContext,
        optionalResolver: NaturalLanguageIntentResolver? = null,
        knownGameAliases: Set<String> = emptySet()
    ): VoiceCommand {
        val clean = VoiceCommandParser.stripLeadingAssistantInvocation(transcript)

        if (currentGameLaunch.matches(clean)) {
            val selected = context.selectedGamePackage?.trim().orEmpty()
            return if (selected.isNotBlank()) {
                VoiceCommand.OpenGame(query = selected)
            } else {
                VoiceCommand.Unknown(transcript)
            }
        }

        if (
            Regex("""\\b(estado termico|thermal status|thermal)\\b""")
                .containsMatchIn(clean)
        ) {
            return VoiceCommand.DeviceStatus
        }

        return VoiceCommandParser.parse(
            transcript = transcript,
            optionalResolver = optionalResolver,
            knownGameAliases = knownGameAliases
        )
    }
}
