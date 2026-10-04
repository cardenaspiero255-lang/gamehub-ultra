package com.cardenaspiero255.gamehubultra.voice

import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

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

internal data class UltraProfileVoiceAlias(
    val alias: String,
    val profile: PerformanceProfile
)

internal class UltraVoiceProfileAliasIndex(
    profileAliases: List<UltraProfileVoiceAlias>
) {
    private val profilesByAlias: Map<String, Set<PerformanceProfile>> =
        profileAliases
            .map { it.copy(alias = normalize(it.alias)) }
            .filter { it.alias.isNotEmpty() }
            .groupBy(UltraProfileVoiceAlias::alias)
            .mapValues { (_, aliases) -> aliases.map(UltraProfileVoiceAlias::profile).toSet() }

    fun resolveProfile(alias: String): PerformanceProfile? =
        profilesByAlias[normalize(alias)]?.singleOrNull()

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

internal class UltraPerGameVoicePreferences(
    preferredActions: Map<String, UltraSafeVoiceAction>
) {
    private val actionsByPackage = preferredActions
        .filterKeys { it.isNotBlank() }

    fun preferredAction(packageName: String): UltraSafeVoiceAction? =
        actionsByPackage[packageName.trim()]
}

internal data class UltraVoicePreferenceBundle(
    val profileAliases: List<UltraProfileVoiceAlias> = emptyList(),
    val phrases: Map<String, UltraSafeVoiceAction> = emptyMap(),
    val perGamePreferredActions: Map<String, UltraSafeVoiceAction> = emptyMap()
)

internal object UltraVoicePreferenceCodec {
    private const val VERSION = "v1"
    private const val PROFILE_ALIAS = "profile"
    private const val PHRASE = "phrase"
    private const val GAME = "game"

    fun export(bundle: UltraVoicePreferenceBundle): String = buildList {
        bundle.profileAliases.forEach { alias ->
            add(record(PROFILE_ALIAS, alias.alias, alias.profile.name))
        }
        bundle.phrases.forEach { (phrase, action) ->
            add(record(PHRASE, phrase, action.name))
        }
        bundle.perGamePreferredActions.forEach { (packageName, action) ->
            add(record(GAME, packageName, action.name))
        }
    }.joinToString("\n")

    fun import(serialized: String): UltraVoicePreferenceBundle? {
        if (serialized.isBlank()) return UltraVoicePreferenceBundle()

        val profileAliases = mutableListOf<UltraProfileVoiceAlias>()
        val phrases = linkedMapOf<String, UltraSafeVoiceAction>()
        val perGame = linkedMapOf<String, UltraSafeVoiceAction>()

        for (line in serialized.lineSequence()) {
            val parts = line.split('|')
            if (parts.size != 4 || parts[0] != VERSION) return null
            val key = decode(parts[2]) ?: return null
            if (key.isBlank()) return null

            when (parts[1]) {
                PROFILE_ALIAS -> {
                    val profile = enumValueOrNull<PerformanceProfile>(parts[3]) ?: return null
                    profileAliases += UltraProfileVoiceAlias(key, profile)
                }
                PHRASE -> {
                    val action = enumValueOrNull<UltraSafeVoiceAction>(parts[3]) ?: return null
                    phrases[key] = action
                }
                GAME -> {
                    val action = enumValueOrNull<UltraSafeVoiceAction>(parts[3]) ?: return null
                    perGame[key] = action
                }
                else -> return null
            }
        }

        val profileIndex = UltraVoiceProfileAliasIndex(profileAliases)
        if (profileAliases.any { profileIndex.resolveProfile(it.alias) == null }) return null

        return UltraVoicePreferenceBundle(profileAliases, phrases, perGame)
    }

    private fun record(type: String, key: String, value: String): String =
        "$VERSION|$type|${encode(key)}|$value"

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private fun decode(value: String): String? =
        runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8.name()) }.getOrNull()

    private inline fun <reified T : Enum<T>> enumValueOrNull(value: String): T? =
        enumValues<T>().singleOrNull { it.name == value }
}
