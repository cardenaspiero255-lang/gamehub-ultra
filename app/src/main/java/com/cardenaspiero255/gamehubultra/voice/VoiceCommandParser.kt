package com.cardenaspiero255.gamehubultra.voice

import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import java.text.Normalizer
import java.util.Locale

object VoiceCommandParser {
    private const val GAME_LAUNCH_VERBS = "abre|abrir|abreme|lanzar|lanza|inicia|iniciar|ejecuta|ejecutar|juega|activa|activar|open me|opens|open|launch|start|run|play|activate"
    private const val PROFILE_ACTION_VERBS = "pon|activa|activar|habilita|habilitar|selecciona|seleccionar|cambia|cambiar|aplica|aplicar|usa|usar|activate|enable|select|switch|apply|use|set"
    private const val PROFILE_OR_LAUNCH_VERBS = GAME_LAUNCH_VERBS + "|" + PROFILE_ACTION_VERBS
    private const val PROFILE_MARKERS = "modo|perfil|mode|profile"
    private const val PROFILE_TARGET_CONNECTORS = "to|for|a|al|para|en|with"
    fun parse(
        transcript: String,
        optionalResolver: NaturalLanguageIntentResolver? = null
    ): VoiceCommand {
        val clean = normalize(transcript)
            .replace(Regex("""\bultra\b"""), " ")
            .trim()
        if (clean.isBlank()) return VoiceCommand.Unknown(transcript)
        if (isUnsafeShellLikeCommand(clean)) return VoiceCommand.Unknown(transcript)

        parseGameAliasDefinition(clean)?.let { return it }

        optionalResolver?.resolve(clean)?.let { return it }

        if (
            clean.contains("temperatura") ||
            clean.contains("temperature") ||
            clean.contains("bateria") ||
            clean.contains("battery") ||
            clean.contains("estado del dispositivo") ||
            clean.contains("device status") ||
            clean == "estado" ||
            clean == "status"
        ) {
            return VoiceCommand.DeviceStatus
        }

        stripAppendedProfileAction(clean)?.let { appended ->
            if (isProfileOnlyInstruction(appended.launchOnly)) {
                return VoiceCommand.SelectProfile(appended.profile)
            }

            val combinedGameQuery = extractGameQuery(
                appended.launchOnly,
                stripProfileSyntax = false
            )
            if (hasLaunchIntent(clean) && combinedGameQuery.isNotBlank()) {
                return VoiceCommand.OpenGame(
                    query = combinedGameQuery,
                    requestedProfile = appended.profile
                )
            }
        }

        profileFromText(clean)?.let { profile ->
            val rawGameQuery = extractGameQuery(clean, stripProfileSyntax = false)
            val profileBelongsToGameTitle =
                hasLaunchIntent(clean) &&
                    !hasExplicitProfileInstruction(clean) &&
                    removeProfileSyntax(rawGameQuery).trim().isNotBlank()

            if (!profileBelongsToGameTitle) {
                val gameQuery = extractGameQuery(clean, stripProfileSyntax = true)
                return if (gameQuery.isNotBlank()) {
                    VoiceCommand.OpenGame(gameQuery, profile)
                } else {
                    VoiceCommand.SelectProfile(profile)
                }
            }
        }

        if (
            clean == "ayuda" ||
            clean == "help" ||
            clean.contains("que puedo hacer") ||
            clean.contains("que comandos puedo hacer") ||
            clean.contains("what can i do") ||
            clean.contains("comandos disponibles") ||
            clean.contains("available commands")
        ) {
            return VoiceCommand.Help
        }

        val gameQuery = extractGameQuery(clean, stripProfileSyntax = false)
        return if (hasExplicitLaunchIntent(clean) && gameQuery.isNotBlank()) {
            VoiceCommand.OpenGame(gameQuery)
        } else {
            VoiceCommand.Unknown(transcript)
        }
    }

    private fun parseGameAliasDefinition(clean: String): VoiceCommand.DefineGameAlias? {
        val spanish = Regex(
            """^cuando diga ([a-z0-9]{2,20}) (?:quiero que )?(?:abras|abre|abreme|lances|lanza|inicies|inicia|ejecutes|ejecuta) (.+)$"""
        ).matchEntire(clean)
        val english = Regex(
            """^when i say ([a-z0-9]{2,20}) (?:i want you to )?(?:open|launch|start|run) (.+)$"""
        ).matchEntire(clean)
        val match = spanish ?: english ?: return null
        val alias = match.groupValues[1].trim()
        val gameQuery = match.groupValues[2].trim()
        if (gameQuery.isBlank()) return null
        return VoiceCommand.DefineGameAlias(alias = alias, gameQuery = gameQuery)
    }

    internal fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("""\p{M}+"""), "")
            .replace(Regex("""[^a-z0-9x4]+"""), " ")
            .trim()
            .replace(Regex("""\s+"""), " ")

    internal fun hasExplicitLaunchIntent(value: String): Boolean {
        val clean = normalize(value)
            .replace(Regex("""\bultra\b"""), " ")
            .replace(Regex("""\bgamehub\b"""), " ")
            .trim()
        return Regex("""^($GAME_LAUNCH_VERBS)\b""").containsMatchIn(clean)
    }

    private fun hasLaunchIntent(clean: String): Boolean = hasExplicitLaunchIntent(clean)

    private fun hasExplicitProfileInstruction(clean: String): Boolean {
        val profileValue =
            """fps balanceado|balanceado|equilibrado|equilibrar|balanced fps|balanced|prioriza interpolacion|priorizar interpolacion|interpolacion|interpolar|frames interpolados|prioritize interpolation|prioritise interpolation|interpolation|interpolate|x4|set x4|maximo rendimiento|alto rendimiento|maximum performance|high performance|configura todo|configure everything|todo al maximo|max everything"""
        return Regex("""\b(y|and)\s+($PROFILE_ACTION_VERBS)\b\s+(?:(el|la|the|to|a|al)\s+)?(($PROFILE_MARKERS)\s+)?($profileValue)(\s+($PROFILE_MARKERS))?\b""")
            .containsMatchIn(clean) ||
            Regex("""\b(en|con|with|using)\s+(($PROFILE_MARKERS)\s+)?($profileValue)(\s+($PROFILE_MARKERS))?\b""")
            .containsMatchIn(clean) ||
            Regex("""\b($PROFILE_MARKERS)\s+($profileValue)\b""")
                .containsMatchIn(clean) ||
            Regex("""\b($profileValue)\s+($PROFILE_MARKERS)\b""")
                .containsMatchIn(clean) ||
            Regex("""^($PROFILE_ACTION_VERBS)\b\s+(to|a|al)\s+(($PROFILE_MARKERS)\s+)?($profileValue)(\s+($PROFILE_MARKERS))?$""")
                .containsMatchIn(clean) ||
            Regex("""^($PROFILE_ACTION_VERBS)\b\s+(to|a|al)\s+(($PROFILE_MARKERS)\s+)?($profileValue)(\s+($PROFILE_MARKERS))?\s+($PROFILE_TARGET_CONNECTORS)\s+\S+""")
                .containsMatchIn(clean) ||
            Regex("""^($PROFILE_ACTION_VERBS)\b\s+($profileValue)(\s+($PROFILE_MARKERS))?\s+($PROFILE_TARGET_CONNECTORS)\s+\S+""")
                .containsMatchIn(clean)
    }

    private data class AppendedProfileAction(
        val launchOnly: String,
        val profile: PerformanceProfile
    )

    private fun stripAppendedProfileAction(clean: String): AppendedProfileAction? {
        val profileValue =
            """fps balanceado|balanceado|equilibrado|equilibrar|balanced fps|balanced|prioriza interpolacion|priorizar interpolacion|interpolacion|interpolar|frames interpolados|prioritize interpolation|prioritise interpolation|interpolation|interpolate|x4|set x4|maximo rendimiento|alto rendimiento|maximum performance|high performance|configura todo|configure everything|todo al maximo|max everything"""
        val match = Regex(
            """\b(y|and)\s+($PROFILE_ACTION_VERBS)\b\s+(?:(el|la|the|to|a|al)\s+)?(($PROFILE_MARKERS)\s+)?($profileValue)(\s+($PROFILE_MARKERS))?\s*$"""
        ).find(clean) ?: return null
        val appendedProfile = profileFromText(match.value) ?: return null

        return AppendedProfileAction(
            launchOnly = clean.removeRange(match.range).trim(),
            profile = appendedProfile
        )
    }

    private fun isProfileOnlyInstruction(clean: String): Boolean {
        val profileValue =
            """fps balanceado|balanceado|equilibrado|equilibrar|balanced fps|balanced|prioriza interpolacion|priorizar interpolacion|interpolacion|interpolar|frames interpolados|prioritize interpolation|prioritise interpolation|interpolation|interpolate|x4|set x4|maximo rendimiento|alto rendimiento|maximum performance|high performance|configura todo|configure everything|todo al maximo|max everything"""
        return Regex(
            """^($PROFILE_ACTION_VERBS)\b\s+(?:(el|la|the|to|a|al)\s+)?(($PROFILE_MARKERS)\s+)?($profileValue)(\s+($PROFILE_MARKERS))?$"""
        ).matches(clean)
    }

    private fun isUnsafeShellLikeCommand(clean: String): Boolean {
        val unsafePatterns = listOf(
            Regex("""\badb\b"""),
            Regex("""\bfastboot\b"""),
            Regex("""\bsettings\s+(put|delete|read|get|list)\b"""),
            Regex("""\bam\s+(start|force\s+stop|broadcast|instrument|kill|profile)\b"""),
            Regex("""\bpm\s+(grant|revoke|install|uninstall|clear|disable|enable|hide|unhide)\b""")
        )
        return unsafePatterns.any { it.containsMatchIn(clean) }
    }

    private fun profileFromText(clean: String): PerformanceProfile? =
        when {
            Regex("""\b(fps balanceado|balanceado|equilibrado|equilibrar|balanced fps|balanced)\b""")
                .containsMatchIn(clean) -> PerformanceProfile.BALANCED
            Regex("""\b(prioriza interpolacion|priorizar interpolacion|interpolacion|interpolar|frames interpolados|prioritize interpolation|prioritise interpolation|interpolation|interpolate)\b""")
                .containsMatchIn(clean) -> PerformanceProfile.FRAME_INTERPOLATION
            Regex("""\b(x4|modo x4|x4 mode|set x4|maximo rendimiento|alto rendimiento|maximum performance|high performance|configura todo|configure everything|todo al maximo|max everything)\b""")
                .containsMatchIn(clean) -> PerformanceProfile.X4
            else -> null
        }

    private fun removeProfileSyntax(clean: String): String =
        clean
            .replace(
                Regex(
                    """\b(y|and)\s+($PROFILE_ACTION_VERBS)\b\s+(($PROFILE_MARKERS)\s+)?(fps balanceado|balanceado|equilibrado|equilibrar|balanced fps|balanced|prioriza interpolacion|priorizar interpolacion|interpolacion|interpolar|frames interpolados|prioritize interpolation|prioritise interpolation|interpolation|interpolate|x4|set x4|maximo rendimiento|alto rendimiento|maximum performance|high performance|configura todo|configure everything|todo al maximo|max everything)(\s+($PROFILE_MARKERS))?\b"""
                ),
                " "
            )
            .replace(Regex("""\b(to|a|al)\s+(($PROFILE_MARKERS)\s+)?(fps balanceado|balanceado|equilibrado|equilibrar|balanced fps|balanced|prioriza interpolacion|priorizar interpolacion|interpolacion|interpolar|frames interpolados|prioritize interpolation|prioritise interpolation|interpolation|interpolate|x4|set x4|maximo rendimiento|alto rendimiento|maximum performance|high performance|configura todo|configure everything|todo al maximo|max everything)(\s+($PROFILE_MARKERS))?\s+($PROFILE_TARGET_CONNECTORS)\b"""), " ")
            .replace(Regex("""\b(to|a|al)\s+(($PROFILE_MARKERS)\s+)?(fps balanceado|balanceado|equilibrado|equilibrar|balanced fps|balanced|prioriza interpolacion|priorizar interpolacion|interpolacion|interpolar|frames interpolados|prioritize interpolation|prioritise interpolation|interpolation|interpolate|x4|set x4|maximo rendimiento|alto rendimiento|maximum performance|high performance|configura todo|configure everything|todo al maximo|max everything)(\s+($PROFILE_MARKERS))?\b"""), " ")
            .replace(Regex("""\b($PROFILE_MARKERS)\s+(fps balanceado|balanceado|equilibrado|equilibrar|balanced fps|balanced|prioriza interpolacion|priorizar interpolacion|interpolacion|interpolar|frames interpolados|prioritize interpolation|prioritise interpolation|interpolation|interpolate|x4|set x4|maximo rendimiento|alto rendimiento|maximum performance|high performance|configura todo|configure everything|todo al maximo|max everything)\s+($PROFILE_TARGET_CONNECTORS)\b"""), " ")
            .replace(Regex("""\b(fps balanceado|balanceado|equilibrado|equilibrar|balanced fps|balanced|prioriza interpolacion|priorizar interpolacion|interpolacion|interpolar|frames interpolados|prioritize interpolation|prioritise interpolation|interpolation|interpolate|x4|set x4|maximo rendimiento|alto rendimiento|maximum performance|high performance|configura todo|configure everything|todo al maximo|max everything)(\s+($PROFILE_MARKERS))?\s+($PROFILE_TARGET_CONNECTORS)\b"""), " ")
            .replace(Regex("""\b(en|con|with|using)\s+(x4|set x4|maximo rendimiento|alto rendimiento|maximum performance|high performance|configura todo|configure everything|todo al maximo|max everything)\s+(modo|perfil|mode|profile)\b"""), " ")
            .replace(Regex("""\b(en|con|with|using)?\s*(modo|perfil|mode|profile)\s+(fps balanceado|balanceado|equilibrado|equilibrar|balanced fps|balanced|prioriza interpolacion|priorizar interpolacion|interpolacion|interpolar|frames interpolados|prioritize interpolation|prioritise interpolation|interpolation|interpolate|x4|maximum performance|high performance|maximo rendimiento|alto rendimiento)\b"""), " ")
            .replace(Regex("""\b(fps balanceado|balanceado|equilibrado|equilibrar|balanced fps|balanced|prioriza interpolacion|priorizar interpolacion|interpolacion|interpolar|frames interpolados|prioritize interpolation|prioritise interpolation|interpolation|interpolate|x4|set x4|maximo rendimiento|alto rendimiento|maximum performance|high performance|configura todo|configure everything|todo al maximo|max everything)\s+(modo|perfil|mode|profile)\b"""), " ")
            .replace(Regex("""\b(fps balanceado|balanceado|equilibrado|equilibrar|balanced fps|balanced)\b"""), " ")
            .replace(Regex("""\b(prioriza interpolacion|priorizar interpolacion|interpolacion|interpolar|frames interpolados|prioritize interpolation|prioritise interpolation|interpolation|interpolate)\b"""), " ")
            .replace(Regex("""\b(x4|set x4|maximo rendimiento|alto rendimiento|maximum performance|high performance|configura todo|configure everything|todo al maximo|max everything)\b"""), " ")

    private fun extractGameQuery(clean: String, stripProfileSyntax: Boolean): String {
        val withoutProfile = if (stripProfileSyntax) {
            removeProfileSyntax(clean)
        } else {
            clean
        }
        val prefixVerbs = if (stripProfileSyntax) {
            PROFILE_OR_LAUNCH_VERBS
        } else {
            GAME_LAUNCH_VERBS
        }

        return withoutProfile
            .replace(Regex("""^\s*(ultra\s+)?(gamehub\s+ultra\s+|gamehub\s+)?($prefixVerbs)\b\s*"""), "")
            .replace(Regex("""\b(la|el|un|una|the|a|an|juego|juegos|game|games)\b"""), " ")
            .replace(Regex("""\b(y|con|en|por favor|and|with|in|please)\b"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}
