package com.cardenaspiero255.gamehubultra.ai

import com.cardenaspiero255.gamehubultra.voice.VoiceCommandParser

enum class UltraGeneralQueryKind {
    GENERAL_KNOWLEDGE,
    CURRENT_DATA,
    COMPARISON_RESEARCH
}

data class UltraGeneralQueryRequest(
    val originalText: String,
    val kind: UltraGeneralQueryKind,
    val requiresInternet: Boolean,
    val requiresFreshData: Boolean,
    val timeoutMillis: Long
)

object UltraGeneralQueryRouter {
    private const val FAST_QUERY_TIMEOUT_MS = 20_000L
    private const val RESEARCH_TIMEOUT_MS = 60_000L

    private val comparisonPattern = Regex(
        """\b(compara|comparar|comparame|vs|versus|cual es mejor|cual tiene mejor|which is better|compare)\b"""
    )
    private val currentDataPattern = Regex(
        """\b(clima|tiempo de hoy|weather|pronostico|forecast|noticias|news|precio|price|precios|prices|cuanto cuesta|cuanto cuestan|how much|cost|costs|salio nuevo|released|fecha de lanzamiento|release date)\b"""
    )
    private val generalKnowledgePattern = Regex(
        """\b(que es|que son|quien es|quienes son|por que|para que sirve|como funciona|explicame|explica|define|cual es|cuales son|donde esta|cuando fue|what is|what are|who is|who are|what does|how does|explain|define|where is|when was)\b"""
    )
    private val englishWhyQuestionPattern = Regex(
        """^(?:(?:hello|hi|please|and)\s+)?why\b"""
    )
    private val technicalProblemPattern = Regex(
        """\b(error|falla|fallo|problema|crash|excepcion|exception|stacktrace|no funciona|no compila|no inicia|como soluciono|solucionar|arreglar|how do i fix|doesn t work|won t compile|fix)\b"""
    )
    private val technicalSubjectPattern = Regex(
        """\b(android|gradle|kotlin|java|python|javascript|typescript|react|sql|api|codigo|programacion|compilar|compilacion|build|sdk|git|github)\b"""
    )
    private val assistantIdentityOnlyPattern = Regex(
        """^(?:ultra )?(?:quien eres|who are you)(?: por favor| please)?$"""
    )
    private val appContextOnlyPattern = Regex(
        """^(?:(?:cual es|dime|muestrame|what is|show me)\s+)?(?:mi juego seleccionado|juego seleccionado|mi perfil|perfil activo|mi configuracion|estado de gamehub|estado de ultra|my selected game|selected game|my profile|active profile|my settings)(?:\s+(?:por favor|please))?$"""
    )
    private val casualConversationPattern = Regex(
        """\b(hola|hello|buenas|buenos dias|buenas tardes|buenas noches|como estas|how are you|que tal|gracias|thanks|estoy aburrido|estoy aburrida|conversa conmigo|habla conmigo)\b"""
    )

    fun classify(transcript: String): UltraGeneralQueryRequest {
        val clean = VoiceCommandParser.stripLeadingAssistantInvocation(transcript)
        return when {
            comparisonPattern.containsMatchIn(clean) ->
                UltraGeneralQueryRequest(
                    originalText = transcript.trim(),
                    kind = UltraGeneralQueryKind.COMPARISON_RESEARCH,
                    requiresInternet = true,
                    requiresFreshData = currentDataPattern.containsMatchIn(clean),
                    timeoutMillis = RESEARCH_TIMEOUT_MS
                )

            currentDataPattern.containsMatchIn(clean) ->
                UltraGeneralQueryRequest(
                    originalText = transcript.trim(),
                    kind = UltraGeneralQueryKind.CURRENT_DATA,
                    requiresInternet = true,
                    requiresFreshData = true,
                    timeoutMillis = FAST_QUERY_TIMEOUT_MS
                )

            englishWhyQuestionPattern.containsMatchIn(clean) ->
                UltraGeneralQueryRequest(
                    originalText = transcript.trim(),
                    kind = UltraGeneralQueryKind.GENERAL_KNOWLEDGE,
                    requiresInternet = true,
                    requiresFreshData = false,
                    timeoutMillis = FAST_QUERY_TIMEOUT_MS
                )

            technicalProblemPattern.containsMatchIn(clean) &&
                technicalSubjectPattern.containsMatchIn(clean) ->
                UltraGeneralQueryRequest(
                    originalText = transcript.trim(),
                    kind = UltraGeneralQueryKind.GENERAL_KNOWLEDGE,
                    requiresInternet = true,
                    requiresFreshData = false,
                    timeoutMillis = FAST_QUERY_TIMEOUT_MS
                )

            appContextOnlyPattern.matches(clean) ->
                UltraGeneralQueryRequest(
                    originalText = transcript.trim(),
                    kind = UltraGeneralQueryKind.GENERAL_KNOWLEDGE,
                    requiresInternet = false,
                    requiresFreshData = false,
                    timeoutMillis = FAST_QUERY_TIMEOUT_MS
                )

            assistantIdentityOnlyPattern.matches(clean) ->
                UltraGeneralQueryRequest(
                    originalText = transcript.trim(),
                    kind = UltraGeneralQueryKind.GENERAL_KNOWLEDGE,
                    requiresInternet = false,
                    requiresFreshData = false,
                    timeoutMillis = FAST_QUERY_TIMEOUT_MS
                )

            generalKnowledgePattern.containsMatchIn(clean) ->
                UltraGeneralQueryRequest(
                    originalText = transcript.trim(),
                    kind = UltraGeneralQueryKind.GENERAL_KNOWLEDGE,
                    requiresInternet = true,
                    requiresFreshData = false,
                    timeoutMillis = FAST_QUERY_TIMEOUT_MS
                )

            casualConversationPattern.containsMatchIn(clean) ->
                UltraGeneralQueryRequest(
                    originalText = transcript.trim(),
                    kind = UltraGeneralQueryKind.GENERAL_KNOWLEDGE,
                    requiresInternet = false,
                    requiresFreshData = false,
                    timeoutMillis = FAST_QUERY_TIMEOUT_MS
                )

            else ->
                UltraGeneralQueryRequest(
                    originalText = transcript.trim(),
                    kind = UltraGeneralQueryKind.GENERAL_KNOWLEDGE,
                    requiresInternet = false,
                    requiresFreshData = false,
                    timeoutMillis = FAST_QUERY_TIMEOUT_MS
                )
        }
    }

}