package com.cardenaspiero255.gamehubultra.voice

import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import java.text.Normalizer
import java.util.Locale

object VoiceCommandParser {
    fun parse(
        transcript: String,
        optionalResolver: NaturalLanguageIntentResolver? = null
    ): VoiceCommand {
        val clean = normalize(transcript)
            .replace(Regex("""\bultra\b"""), " ")
            .trim()
        if (clean.isBlank()) return VoiceCommand.Unknown(transcript)
        if (isUnsafeShellLikeCommand(clean)) return VoiceCommand.Unknown(transcript)

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

        profileFromText(clean)?.let { profile ->
            val gameQuery = extractGameQuery(clean)
            return if (gameQuery.isNotBlank()) {
                VoiceCommand.OpenGame(gameQuery, profile)
            } else {
                VoiceCommand.SelectProfile(profile)
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

        val gameQuery = extractGameQuery(clean)
        return if (gameQuery.isNotBlank()) {
            VoiceCommand.OpenGame(gameQuery)
        } else {
            VoiceCommand.Unknown(transcript)
        }
    }

    internal fun normalize(value: String): String = 
        Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("""\p{M}+"""), "")
            .replace(Regex("""[^a-z0-9x4]+"""), " ")
            .trim()
            .replace(Regex("""\s+"""), " ")

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

    private fun extractGameQuery(clean: String): String {
        val withoutProfile = clean
            .replace(Regex("""\b(fps balanceado|balanceado|equilibrado|equilibrar|balanced fps|balanced)\b"""), " ")
            .replace(Regex("""\b(prioriza interpolacion|priorizar interpolacion|interpolacion|interpolar|frames interpolados|prioritize interpolation|prioritise interpolation|interpolation|interpolate)\b"""), " ")
            .replace(Regex("""\b(x4|modo x4|x4 mode|set x4|maximo rendimiento|alto rendimiento|maximum performance|high performance|configura todo|configure everything|todo al maximo|max everything)\b"""), " ")

        return withoutProfile
            .replace(Regex("""^\s*(ultra\s+)?(gamehub\s+ultra\s+|gamehub\s+)?(abre|abrir|abreme|lanzar|lanza|inicia|iniciar|ejecuta|ejecutar|juega|pon|open|opens|open me|launch|start|run|play)\s*"""), "")
            .replace(Regex("""\b(la|el|un|una|the|a|an|juego|juegos|game|games)\b"""), " ")
            .replace(Regex("""\b(y|con|en|por favor|and|with|in|please)\b"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}
