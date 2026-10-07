package com.cardenaspiero255.gamehubultra.ai

import com.cardenaspiero255.gamehubultra.voice.VoiceCommandParser
import java.util.UUID

enum class UltraGeneralQueryKind {
    GENERAL_KNOWLEDGE,
    CURRENT_DATA,
    COMPARISON_RESEARCH
}

enum class UltraVerificationMode {
    LOCAL,
    OPTIONAL,
    REQUIRED
}

data class UltraGeneralQueryRequest(
    val originalText: String,
    val kind: UltraGeneralQueryKind,
    val requiresInternet: Boolean,
    val requiresFreshData: Boolean,
    val timeoutMillis: Long,
    val verificationMode: UltraVerificationMode = when {
        requiresFreshData -> UltraVerificationMode.REQUIRED
        kind == UltraGeneralQueryKind.CURRENT_DATA ->
            UltraVerificationMode.REQUIRED
        kind == UltraGeneralQueryKind.COMPARISON_RESEARCH ->
            UltraVerificationMode.REQUIRED
        requiresInternet -> UltraVerificationMode.REQUIRED
        else -> UltraVerificationMode.LOCAL
    },
    val correlationId: String = UUID.randomUUID().toString()
)

object UltraGeneralQueryRouter {
    private const val FAST_QUERY_TIMEOUT_MS = 20_000L
    private const val RESEARCH_TIMEOUT_MS = 60_000L

    private val comparisonPattern = Regex(
        """\b(compara|comparar|comparame|vs|versus|cual es mejor|cual tiene mejor|which is better|compare)\b"""
    )
    private val currentDataPattern = Regex(
        """\b(clima|tiempo de hoy|weather|pronostico|forecast|temperatura|temperature|noticias|news|novedades|updates?|latest|newest|precio|price|precios|prices|cuanto cuesta|cuanto cuestan|how much|cost|costs|salio nuevo|released|cuando sale|cuando se lanza|fecha de lanzamiento|fecha de salida|release date|launch date|coming out|security patch|parche de seguridad)\b"""
    )
    private val explicitCurrentValuePattern = Regex(
        """\b(precio (?:de|del)|precios de|price of|prices of|cuanto cuesta|cuanto cuestan|how much|[a-z0-9]+\s+s\s+(?:price|cost))\b"""
    )
    private val explicitCostQuestionPattern = Regex(
        """(?:\bwhat\s+(?:does|do)\s+.{1,80}\s+cost\s*[?.!]?\s*$|\bwhat\s+is\s+(?:the\s+)?cost\s+(?:of|for)\s+(?:a|an)\s+.{1,80}\s*[?.!]?\s*$|\bwhat\s+is\s+(?:the\s+)?price\s+(?:of|for)\b|\bhow\s+much\s+(?:is|are|does|do)\b|\bcuanto\s+cuesta(?:n)?\b)"""
    )
    private val currentQualifierPattern = Regex(
        """\b(actual|actualmente|ahora|hoy|esta noche|esta semana|current|currently|latest|newest|today|tomorrow|tonight|this week)\b"""
    )
    private val explicitWeatherValuePattern = Regex(
        """\b(que temperatura hace|temperatura (?:actual|ahora|hoy|en)|temperature (?:now|today|in)|clima (?:actual|ahora|hoy|manana|en)|weather (?:now|today|tomorrow|in)|pronostico (?:de|para|en|hoy|manana)|forecast (?:for|in|today|tomorrow))\b"""
    )
    private val explicitFreshUpdatePattern = Regex(
        """\b(?:novedades|updates?|latest|newest|security patch|parche de seguridad|cuando sale|cuando se lanza|fecha de lanzamiento|fecha de salida|release date|launch date|coming out)\b"""
    )
    private val definitionPattern = Regex(
        """\b(que es|que son|que significa|cual es el significado de|significado de|definicion de|what is|what are|what does|meaning of|define)\b"""
    )
    private val conversationalTopicPattern = Regex(
        """^(?:(?:por favor|please)\s+)?(?:hablame|cuentame|dime(?:\s+algo)?|quiero\s+saber|que\s+sabes|dame\s+informacion|informame|describeme|tell\s+me|tell\s+me\s+about|talk\s+to\s+me|describe)\b(?:\s+(?:de|del|sobre|acerca\s+de|about))?\s+\S.+$"""
    )
    private val conversationalFreshTopicPattern = Regex(
        """\b(noticias|news|novedades|updates?|latest|newest|precio|precios|price|prices|cuanto cuesta|cuanto cuestan|how much|actual|actualmente|ahora|hoy|current|currently|today|release date|fecha de lanzamiento|fecha de salida|cuando sale|cuando se lanza)\b"""
    )
    private val broadFactualPattern = Regex(
        """\b(cuantos|cuantas|como se llama|como se llaman|how many|how old|what year|which country)\b"""
    )
    private val generalKnowledgePattern = Regex(
        """\b(que es|que son|que significa|cual es el significado de|significado de|definicion de|quien es|quienes son|por que|para que sirve|como funciona|explicame|explica|define|cual es|cuales son|donde esta|cuando fue|what is|what are|who is|who are|what does|meaning of|how does|explain|define|where is|when was)\b"""
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

    internal fun isConversationalTopicRequest(transcript: String): Boolean =
        conversationalTopicPattern.containsMatchIn(
            VoiceCommandParser.stripLeadingAssistantInvocation(transcript)
        )

    private fun request(
        transcript: String,
        kind: UltraGeneralQueryKind,
        verificationMode: UltraVerificationMode,
        requiresFreshData: Boolean,
        timeoutMillis: Long
    ): UltraGeneralQueryRequest =
        UltraGeneralQueryRequest(
            originalText = transcript.trim(),
            kind = kind,
            requiresInternet = verificationMode == UltraVerificationMode.REQUIRED,
            requiresFreshData = requiresFreshData,
            timeoutMillis = timeoutMillis,
            verificationMode = verificationMode
        )

    fun classify(transcript: String): UltraGeneralQueryRequest {
        val clean = VoiceCommandParser.stripLeadingAssistantInvocation(transcript)
        return when {
            comparisonPattern.containsMatchIn(clean) ->
                request(
                    transcript = transcript,
                    kind = UltraGeneralQueryKind.COMPARISON_RESEARCH,
                    verificationMode = UltraVerificationMode.REQUIRED,
                    requiresFreshData = currentDataPattern.containsMatchIn(clean),
                    timeoutMillis = RESEARCH_TIMEOUT_MS
                )

            appContextOnlyPattern.matches(clean) ||
                assistantIdentityOnlyPattern.matches(clean) ->
                request(
                    transcript = transcript,
                    kind = UltraGeneralQueryKind.GENERAL_KNOWLEDGE,
                    verificationMode = UltraVerificationMode.LOCAL,
                    requiresFreshData = false,
                    timeoutMillis = FAST_QUERY_TIMEOUT_MS
                )

            currentQualifierPattern.containsMatchIn(clean) &&
                (
                    generalKnowledgePattern.containsMatchIn(clean) ||
                        broadFactualPattern.containsMatchIn(clean)
                    ) ->
                request(
                    transcript = transcript,
                    kind = UltraGeneralQueryKind.CURRENT_DATA,
                    verificationMode = UltraVerificationMode.REQUIRED,
                    requiresFreshData = true,
                    timeoutMillis = FAST_QUERY_TIMEOUT_MS
                )

            explicitCurrentValuePattern.containsMatchIn(clean) ||
                explicitWeatherValuePattern.containsMatchIn(clean) ||
                (
                    explicitFreshUpdatePattern.containsMatchIn(clean) &&
                        !generalKnowledgePattern.containsMatchIn(clean)
                    ) ->
                request(
                    transcript = transcript,
                    kind = UltraGeneralQueryKind.CURRENT_DATA,
                    verificationMode = UltraVerificationMode.REQUIRED,
                    requiresFreshData = true,
                    timeoutMillis = FAST_QUERY_TIMEOUT_MS
                )

            explicitCostQuestionPattern.containsMatchIn(clean) ->
                request(
                    transcript = transcript,
                    kind = UltraGeneralQueryKind.CURRENT_DATA,
                    verificationMode = UltraVerificationMode.REQUIRED,
                    requiresFreshData = true,
                    timeoutMillis = FAST_QUERY_TIMEOUT_MS
                )

            definitionPattern.containsMatchIn(clean) ||
                (
                    conversationalTopicPattern.containsMatchIn(clean) &&
                        !conversationalFreshTopicPattern.containsMatchIn(clean)
                    ) ->
                request(
                    transcript = transcript,
                    kind = UltraGeneralQueryKind.GENERAL_KNOWLEDGE,
                    verificationMode = UltraVerificationMode.OPTIONAL,
                    requiresFreshData = false,
                    timeoutMillis = FAST_QUERY_TIMEOUT_MS
                )

            currentDataPattern.containsMatchIn(clean) &&
                !generalKnowledgePattern.containsMatchIn(clean) ->
                request(
                    transcript = transcript,
                    kind = UltraGeneralQueryKind.CURRENT_DATA,
                    verificationMode = UltraVerificationMode.REQUIRED,
                    requiresFreshData = true,
                    timeoutMillis = FAST_QUERY_TIMEOUT_MS
                )

            englishWhyQuestionPattern.containsMatchIn(clean) ||
                (
                    technicalProblemPattern.containsMatchIn(clean) &&
                        technicalSubjectPattern.containsMatchIn(clean)
                    ) ||
                generalKnowledgePattern.containsMatchIn(clean) ||
                broadFactualPattern.containsMatchIn(clean) ->
                request(
                    transcript = transcript,
                    kind = UltraGeneralQueryKind.GENERAL_KNOWLEDGE,
                    verificationMode = UltraVerificationMode.OPTIONAL,
                    requiresFreshData = false,
                    timeoutMillis = FAST_QUERY_TIMEOUT_MS
                )

            casualConversationPattern.containsMatchIn(clean) ->
                request(
                    transcript = transcript,
                    kind = UltraGeneralQueryKind.GENERAL_KNOWLEDGE,
                    verificationMode = UltraVerificationMode.LOCAL,
                    requiresFreshData = false,
                    timeoutMillis = FAST_QUERY_TIMEOUT_MS
                )

            else ->
                request(
                    transcript = transcript,
                    kind = UltraGeneralQueryKind.GENERAL_KNOWLEDGE,
                    verificationMode = UltraVerificationMode.LOCAL,
                    requiresFreshData = false,
                    timeoutMillis = FAST_QUERY_TIMEOUT_MS
                )
        }
    }
}
