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
        if (clean.isBlank()) return VoiceCommand.Unknown(transcript)

        optionalResolver?.resolve(transcript)?.let { return it }

        if (
            clean.contains("temperatura") ||
            clean.contains("bateria") ||
            clean.contains("estado del dispositivo") ||
            clean == "estado"
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
            clean.contains("que puedo hacer") ||
            clean.contains("comandos disponibles")
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

    private fun profileFromText(clean: String): PerformanceProfile? =
        when {
            Regex("""\b(fps balanceado|balanceado|equilibrado|equilibrar)\b""")
                .containsMatchIn(clean) -> PerformanceProfile.BALANCED
            Regex("""\b(priorizar interpolacion|interpolacion|interpolar|frames interpolados)\b""")
                .containsMatchIn(clean) -> PerformanceProfile.FRAME_INTERPOLATION
            Regex("""\b(x4|modo x4|maximo rendimiento|alto rendimiento|configura todo|todo al maximo)\b""")
                .containsMatchIn(clean) -> PerformanceProfile.X4
            else -> null
        }

    private fun extractGameQuery(clean: String): String {
        val withoutProfile = clean
            .replace(Regex("""\b(fps balanceado|balanceado|equilibrado|equilibrar)\b"""), " ")
            .replace(Regex("""\b(priorizar interpolacion|interpolacion|interpolar|frames interpolados)\b"""), " ")
            .replace(Regex("""\b(x4|modo x4|maximo rendimiento|alto rendimiento|configura todo|todo al maximo)\b"""), " ")

        return withoutProfile
            .replace(
                Regex("""^\s*(gamehub\s+)?(abre|abrir|abreme|lanzar|lanza|inicia|iniciar|ejecuta|ejecutar|juega|pon)\s*"""),
                ""
            )
            .replace(Regex("""\b(la|el|un|una|juego|juegos)\b"""), " ")
            .replace(Regex("""\b(y|con|en|por favor)\b"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}
