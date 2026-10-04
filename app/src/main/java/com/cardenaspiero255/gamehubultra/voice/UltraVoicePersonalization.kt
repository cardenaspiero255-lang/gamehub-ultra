package com.cardenaspiero255.gamehubultra.voice

internal data class UltraGameVoiceAlias(
    val alias: String,
    val packageName: String
)

internal class UltraVoiceAliasIndex(
    gameAliases: List<UltraGameVoiceAlias>
) {
    private val gamesByAlias: Map<String, Set<String>> =
        gameAliases
            .map { it.copy(alias = normalize(it.alias)) }
            .filter { it.alias.isNotEmpty() && it.packageName.isNotBlank() }
            .groupBy(UltraGameVoiceAlias::alias)
            .mapValues { (_, aliases) -> aliases.map(UltraGameVoiceAlias::packageName).toSet() }

    fun resolveGame(alias: String): String? =
        gamesByAlias[normalize(alias)]?.singleOrNull()

    private fun normalize(value: String): String =
        VoiceCommandParser.canonicalGameAliasKey(value)
}

internal enum class UltraSafeVoiceAction {
    SelectBalancedProfile
}

internal class UltraVoicePhrasePreferences(
    phrases: Map<String, UltraSafeVoiceAction>
) {
    private val actionsByPhrase: Map<String, UltraSafeVoiceAction> =
        phrases.mapKeys { (phrase, _) -> normalize(phrase) }
            .filterKeys(String::isNotEmpty)

    fun resolve(phrase: String): UltraSafeVoiceAction? =
        actionsByPhrase[normalize(phrase)]

    private fun normalize(value: String): String =
        VoiceCommandParser.stripLeadingAssistantInvocation(value).trim()
}
