package com.cardenaspiero255.gamehubultra.ai

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

fun interface UltraPublicKnowledgeTransport {
    fun get(
        url: String,
        timeoutMillis: Long
    ): UltraResearchHttpResponse

    fun cancelActiveRequest(worker: Thread) = Unit
}

object HttpUrlConnectionUltraPublicKnowledgeTransport : UltraPublicKnowledgeTransport {
    private val activeConnections =
        ConcurrentHashMap<Thread, HttpURLConnection>()

    override fun get(
        url: String,
        timeoutMillis: Long
    ): UltraResearchHttpResponse {
        if (Thread.currentThread().isInterrupted) {
            throw InterruptedException("Wikimedia request cancelled")
        }

        val worker = Thread.currentThread()
        val connection = URL(url).openConnection() as HttpURLConnection
        activeConnections[worker] = connection
        val safeTimeout = timeoutMillis.coerceIn(
            MIN_NETWORK_TIMEOUT_MS,
            MAX_NETWORK_TIMEOUT_MS
        ).toInt()
        val deadlineNanos = System.nanoTime() +
            TimeUnit.MILLISECONDS.toNanos(safeTimeout.toLong())
        connection.requestMethod = "GET"
        connection.connectTimeout = safeTimeout
        // Use bounded socket waits so cancellation can take effect promptly even
        // while HttpURLConnection is blocked waiting for response headers.
        connection.readTimeout = minOf(safeTimeout, CANCELLATION_POLL_TIMEOUT_MS)
        connection.setRequestProperty(
            "User-Agent",
            "GameHub-Ultra/0.3 (Android; public knowledge fallback; " +
                "github.com/cardenaspiero255-lang/gamehub-ultra)"
        )
        connection.setRequestProperty("Accept", "application/json")
        return try {
            val status = connection.responseCode
            val contentLength = connection.contentLengthLong
            if (contentLength > MAX_RESPONSE_BYTES) {
                throw IllegalStateException(
                    "Wikimedia response is too large: $contentLength bytes"
                )
            }

            val stream = if (status in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val body = stream?.use { input ->
                val output = ByteArrayOutputStream(
                    minOf(
                        MAX_RESPONSE_BYTES,
                        contentLength
                            .takeIf { it > 0L }
                            ?.coerceAtMost(MAX_RESPONSE_BYTES.toLong())
                            ?.toInt()
                            ?: DEFAULT_RESPONSE_BUFFER_BYTES
                    )
                )
                val buffer = ByteArray(8_192)
                while (true) {
                    val remainingNanos = deadlineNanos - System.nanoTime()
                    if (remainingNanos <= 0L) {
                        throw IllegalStateException(
                            "Wikimedia response read deadline exceeded"
                        )
                    }
                    connection.readTimeout = TimeUnit.NANOSECONDS
                        .toMillis(remainingNanos)
                        .coerceIn(1L, safeTimeout.toLong())
                        .toInt()

                    val read = input.read(buffer)
                    if (read < 0) break
                    if (output.size() + read > MAX_RESPONSE_BYTES) {
                        throw IllegalStateException(
                            "Wikimedia response exceeded maximum size"
                        )
                    }
                    output.write(buffer, 0, read)
                }
                output.toString(Charsets.UTF_8.name())
            }.orEmpty()

            UltraResearchHttpResponse(
                statusCode = status,
                body = body
            )
        } finally {
            activeConnections.remove(worker, connection)
            connection.disconnect()
        }
    }

    override fun cancelActiveRequest(worker: Thread) {
        activeConnections.remove(worker)?.disconnect()
        worker.interrupt()
    }

    private const val CANCELLATION_POLL_TIMEOUT_MS = 500
    private const val MIN_NETWORK_TIMEOUT_MS = 250L
    private const val MAX_NETWORK_TIMEOUT_MS = 5_000L
    private const val MAX_RESPONSE_BYTES = 512 * 1024
    private const val DEFAULT_RESPONSE_BUFFER_BYTES = 16 * 1024
}

/**
 * Keyless public fallback for stable general knowledge.
 *
 * It intentionally refuses fresh/current/comparison queries. Its only job is
 * to keep ordinary stable questions answerable when the private research
 * backend is unavailable or misconfigured.
 */
class WikimediaUltraResearchProvider(
    private val transport: UltraPublicKnowledgeTransport =
        HttpUrlConnectionUltraPublicKnowledgeTransport
) : UltraResearchProvider {

    override val id: String = "wikimedia-public"

    override fun cancelActiveRequest(worker: Thread) {
        transport.cancelActiveRequest(worker)
    }

    override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence =
        when (val result = fetchResult(request)) {
            is UltraProviderResult.Evidence -> result.evidence
            is UltraProviderResult.Abstained ->
                error(result.message ?: "Wikimedia abstained: " + result.reasonCode)
            is UltraProviderResult.Failure ->
                error(result.message ?: "Wikimedia failed: " + result.reasonCode)
        }

    override fun fetchResult(request: UltraGeneralQueryRequest): UltraProviderResult {
        if (
            request.kind != UltraGeneralQueryKind.GENERAL_KNOWLEDGE ||
            request.verificationMode != UltraVerificationMode.OPTIONAL ||
            request.requiresFreshData
        ) {
            return UltraProviderResult.Abstained(
                reasonCode = "PUBLIC_FALLBACK_NOT_APPLICABLE",
                message = "El fallback público solo se usa para conocimiento estable."
            )
        }

        val currentQuestion = currentQuestionText(request.originalText)
        if (isDependentFollowUpWithoutSubject(currentQuestion)) {
            return UltraProviderResult.Abstained(
                reasonCode = "PUBLIC_FALLBACK_CONTEXT_REQUIRED",
                message = "Necesito el tema explícito para usar el fallback público."
            )
        }

        val topic = extractTopic(request.originalText)
        if (topic.isBlank()) {
            return UltraProviderResult.Abstained(
                reasonCode = "PUBLIC_FALLBACK_EMPTY_QUERY",
                message = "No pude extraer un tema estable de la pregunta."
            )
        }

        val deadlineNanos = System.nanoTime() +
            java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(
                request.timeoutMillis.coerceAtLeast(1L)
            )
        val perCallBudgetMillis = (request.timeoutMillis / 4L)
            .coerceIn(250L, 1_200L)

        val searchUrl = buildString {
            append("https://es.wikipedia.org/w/api.php")
            append("?action=query&list=search&srlimit=1&format=json&origin=*")
            append("&srsearch=")
            append(urlEncode(topic))
        }

        val searchTimeout = remainingCallTimeoutMillis(
            deadlineNanos = deadlineNanos,
            perCallBudgetMillis = perCallBudgetMillis
        ) ?: return timeoutFailure("wikimedia-search")

        val searchResponse = when (
            val attempt = getSafely(searchUrl, searchTimeout)
        ) {
            is TransportOutcome.Success -> attempt.response
            is TransportOutcome.Failure ->
                return attempt.toProviderResult("wikimedia-search")
        }
        if (searchResponse.statusCode !in 200..299) {
            return httpFailure(searchResponse.statusCode, "wikimedia-search")
        }

        val title = jsonString(searchResponse.body, "title")
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: return UltraProviderResult.Abstained(
                reasonCode = "PUBLIC_FALLBACK_NO_RESULT",
                message = "Wikimedia no encontró un resultado utilizable.",
                stage = "wikimedia-search"
            )
        val searchSnippet = jsonString(searchResponse.body, "snippet").orEmpty()
        val directTitleMatch = titleMatchesTopic(
            topic = topic,
            title = title,
            currentQuestion = currentQuestion,
            searchSnippet = ""
        )
        val verifiedTranslatedTitleMatch =
            !directTitleMatch &&
                isEnglishDefinitionQuestion(currentQuestion) &&
                translatedSpanishTitleMatchesEnglishTopic(
                    topic = topic,
                    spanishTitle = title,
                    deadlineNanos = deadlineNanos,
                    perCallBudgetMillis = perCallBudgetMillis
                )
        val titleOrTrustedMatch =
            directTitleMatch || verifiedTranslatedTitleMatch
        val snippetOnlyMatch =
            !titleOrTrustedMatch &&
                titleMatchesTopic(
                    topic = topic,
                    title = title,
                    currentQuestion = currentQuestion,
                    searchSnippet = searchSnippet
                )

        if (!titleOrTrustedMatch && !snippetOnlyMatch) {
            return UltraProviderResult.Abstained(
                reasonCode = "PUBLIC_FALLBACK_IRRELEVANT_RESULT",
                message = "Wikimedia encontró una página que no coincide con el tema.",
                stage = "wikimedia-search"
            )
        }

        val extractUrl = buildString {
            append("https://es.wikipedia.org/w/api.php")
            append("?action=query&prop=extracts%7Cinfo%7Cpageprops&inprop=url")
            append("&exintro=1&explaintext=1&redirects=1&format=json&origin=*")
            append("&titles=")
            append(urlEncode(title))
        }

        val extractTimeout = remainingCallTimeoutMillis(
            deadlineNanos = deadlineNanos,
            perCallBudgetMillis = perCallBudgetMillis
        ) ?: return timeoutFailure("wikimedia-extract")

        val extractResponse = when (
            val attempt = getSafely(extractUrl, extractTimeout)
        ) {
            is TransportOutcome.Success -> attempt.response
            is TransportOutcome.Failure ->
                return attempt.toProviderResult("wikimedia-extract")
        }
        if (extractResponse.statusCode !in 200..299) {
            return httpFailure(
                extractResponse.statusCode,
                "wikimedia-extract"
            )
        }

        if (jsonHasKey(extractResponse.body, "disambiguation")) {
            return UltraProviderResult.Abstained(
                reasonCode = "PUBLIC_FALLBACK_DISAMBIGUATION",
                message = "Wikimedia devolvió una página de desambiguación.",
                stage = "wikimedia-extract"
            )
        }

        val extract = jsonString(extractResponse.body, "extract")
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: return UltraProviderResult.Abstained(
                reasonCode = "PUBLIC_FALLBACK_NO_EVIDENCE",
                message = "Wikimedia no devolvió una explicación utilizable.",
                stage = "wikimedia-extract"
            )

        if (snippetOnlyMatch && !textMatchesTopic(topic, extract)) {
            return UltraProviderResult.Abstained(
                reasonCode = "PUBLIC_FALLBACK_IRRELEVANT_RESULT",
                message = "Wikimedia no confirmó el tema en el contenido del artículo.",
                stage = "wikimedia-extract"
            )
        }

        val canonicalUrl = jsonString(
            extractResponse.body,
            "canonicalurl"
        )
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: "https://es.wikipedia.org/wiki/" +
                urlEncode(title.replace(' ', '_'))

        val concise = conciseIntro(extract)
        return UltraProviderResult.Evidence(
            UltraResearchEvidence(
                claimKey = "general:" + slug(title),
                value = normalizeForComparison(concise),
                displayText = concise,
                sourceId = canonicalUrl,
                independentSourceCount = 1,
                authoritative = true
            )
        )
    }

    private sealed interface TransportOutcome {
        data class Success(
            val response: UltraResearchHttpResponse
        ) : TransportOutcome

        data class Failure(
            val message: String?
        ) : TransportOutcome
    }

    private fun getSafely(
        url: String,
        timeoutMillis: Long
    ): TransportOutcome =
        try {
            TransportOutcome.Success(transport.get(url, timeoutMillis))
        } catch (error: Exception) {
            TransportOutcome.Failure(error.message)
        }

    private fun TransportOutcome.Failure.toProviderResult(
        stage: String
    ): UltraProviderResult.Failure =
        UltraProviderResult.Failure(
            reasonCode = "PUBLIC_FALLBACK_NETWORK_FAILURE",
            message = message,
            retryable = true,
            stage = stage
        )

    private fun httpFailure(
        status: Int,
        stage: String
    ): UltraProviderResult.Failure =
        UltraProviderResult.Failure(
            reasonCode = when (status) {
                408, 504 -> "UPSTREAM_TIMEOUT"
                429 -> "UPSTREAM_RATE_LIMIT"
                500, 502, 503 -> "UPSTREAM_UNAVAILABLE"
                else -> "PUBLIC_FALLBACK_HTTP_FAILURE"
            },
            message = "Wikimedia respondió HTTP " + status + ".",
            retryable = status in setOf(408, 429, 500, 502, 503, 504),
            stage = stage,
            upstreamStatus = status
        )

    private fun remainingCallTimeoutMillis(
        deadlineNanos: Long,
        perCallBudgetMillis: Long
    ): Long? {
        val remainingNanos = deadlineNanos - System.nanoTime()
        if (remainingNanos <= 0L) return null

        val remainingMillis = java.util.concurrent.TimeUnit.NANOSECONDS
            .toMillis(remainingNanos)
        if (remainingMillis < 250L) return null

        return minOf(
            perCallBudgetMillis,
            remainingMillis
        ).coerceAtLeast(250L)
    }

    private fun timeoutFailure(
        stage: String
    ): UltraProviderResult.Failure =
        UltraProviderResult.Failure(
            reasonCode = "UPSTREAM_TIMEOUT",
            message = "Wikimedia agotó el presupuesto de tiempo.",
            retryable = true,
            stage = stage
        )

    private fun currentQuestionText(originalText: String): String =
        originalText
            .substringAfterLast("Pregunta actual:", originalText)
            .trim()

    private fun isDependentFollowUpWithoutSubject(value: String): Boolean {
        val stripped = value.replace(
            Regex(
                """^\s*(?:gamehub\s+ultra|gamehub|ultra)\s*[,;:.-]?\s*""",
                RegexOption.IGNORE_CASE
            ),
            ""
        ).trimStart(' ', '¿', '¡')

        if (
            Regex(
                """^(?:y\s+)?él\b""",
                RegexOption.IGNORE_CASE
            ).containsMatchIn(stripped)
        ) {
            return true
        }

        val normalized = normalizeForComparison(stripped)
            .replace(Regex("""[^a-z0-9]+"""), " ")
            .trim()

        if (normalized.isBlank()) return false

        val explicitSubjectAfterPronoun = Regex(
            """\b(?:eso|esto|esa|ese|aquello)\s+(?:de|sobre)\s+(?:(?:el|la|los|las|un|una)\s+)?[a-z0-9]{2,}\b"""
        )
        if (explicitSubjectAfterPronoun.containsMatchIn(normalized)) {
            return false
        }

        val pronounLed = Regex(
            """^(?:y\s+)?(?:eso|esto|esa|ese|aquello|ella|ellos|ellas)\b"""
        )
        val questionWithPronoun = Regex(
            """^(?:y\s+)?(?:como|que|por que|cuando|donde)\b.*\b(?:eso|esto|esa|ese|aquello)\b"""
        )
        return pronounLed.containsMatchIn(normalized) ||
            questionWithPronoun.containsMatchIn(normalized)
    }

    private fun titleMatchesTopic(
        topic: String,
        title: String,
        currentQuestion: String,
        searchSnippet: String
    ): Boolean {
        val normalizedTopic = normalizedTopicPhrase(topic)
        val normalizedTitle = normalizedTopicPhrase(title)
        if (
            normalizedTopic.isNotBlank() &&
            normalizedTopic == normalizedTitle
        ) {
            return true
        }

        val compactAliases = setOf(
            "tiktok",
            "youtube",
            "whatsapp",
            "instagram",
            "facebook",
            "snapchat",
            "telegram"
        )
        val compactTopic = normalizedTopic.replace(" ", "")
        val compactTitle = normalizedTitle.replace(" ", "")
        val rawTopicTokens = normalizedTopic
            .split(' ')
            .filter(String::isNotBlank)
        val safeSpacingVariant =
            rawTopicTokens.size >= 2 &&
                rawTopicTokens.all { token ->
                    token.length >= 3 && token !in TOPIC_STOP_WORDS
                }
        if (
            compactTopic == compactTitle &&
            compactTopic.isNotBlank() &&
            (compactTopic in compactAliases || safeSpacingVariant)
        ) {
            return true
        }

        val topicTokens = meaningfulTokens(topic)
        val titleTokens = meaningfulTokens(title)
        if (
            topicTokens.isNotEmpty() &&
            titleTokens.isNotEmpty() &&
            topicTokens.intersect(titleTokens).isNotEmpty()
        ) {
            return true
        }

        val snippetTokens = meaningfulTokens(searchSnippet)
        val snippetRelated = topicTokens.any { topicToken ->
            snippetTokens.any { snippetToken -> lexicallyRelated(topicToken, snippetToken) }
        }
        if (topicTokens.isNotEmpty() && snippetTokens.isNotEmpty() && snippetRelated) return true

        return false
    }

    private fun translatedSpanishTitleMatchesEnglishTopic(
        topic: String,
        spanishTitle: String,
        deadlineNanos: Long,
        perCallBudgetMillis: Long
    ): Boolean {
        val validationUrl = buildString {
            append("https://en.wikipedia.org/w/api.php")
            append("?action=query&generator=search&gsrlimit=1")
            append("&prop=langlinks&lllang=es&lllimit=1")
            append("&format=json&origin=*&gsrsearch=")
            append(urlEncode(topic))
        }

        val validationTimeout = remainingCallTimeoutMillis(
            deadlineNanos = deadlineNanos,
            perCallBudgetMillis = perCallBudgetMillis
        ) ?: return false

        val response = when (
            val attempt = getSafely(validationUrl, validationTimeout)
        ) {
            is TransportOutcome.Success -> attempt.response
            is TransportOutcome.Failure -> return false
        }
        if (response.statusCode !in 200..299) return false

        val englishTitle = jsonString(response.body, "title")
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: return false
        val translatedTitle = jsonString(response.body, "*")
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: return false

        return englishTitleCoversTopic(topic, englishTitle) &&
            normalizedTopicPhrase(translatedTitle) ==
                normalizedTopicPhrase(spanishTitle)
    }

    private fun englishTitleCoversTopic(
        topic: String,
        englishTitle: String
    ): Boolean {
        val topicTokens = meaningfulTokens(topic)
        val titleTokens = meaningfulTokens(englishTitle)
        if (topicTokens.isEmpty() || titleTokens.isEmpty()) return false

        return topicTokens.all { topicToken ->
            titleTokens.any { titleToken ->
                topicToken == titleToken ||
                    lexicallyRelated(topicToken, titleToken)
            }
        }
    }

    private fun isEnglishDefinitionQuestion(value: String): Boolean {
        val stripped = value.replace(
            Regex(
                """^\s*(?:gamehub\s+ultra|gamehub|ultra)\s*[,;:.-]?\s*""",
                RegexOption.IGNORE_CASE
            ),
            ""
        ).trimStart(' ', '¿', '¡')

        return Regex(
            """^(?:what\s+(?:is|are|was|were|does)|who\s+(?:is|was|are|were)|meaning\s+of)\b""",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(stripped)
    }

    private fun normalizedTopicPhrase(value: String): String =
        normalizeForComparison(value)
            .replace(Regex("""[^a-z0-9]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun meaningfulTokens(value: String): Set<String> =
        normalizeForComparison(value)
            .replace(Regex("""[^a-z0-9]+"""), " ")
            .split(' ')
            .asSequence()
            .map(String::trim)
            .filter { it.length >= 3 }
            .filterNot(TOPIC_STOP_WORDS::contains)
            .toSet()

    private fun textMatchesTopic(
        topic: String,
        text: String
    ): Boolean {
        val topicTokens = meaningfulTokens(topic)
        val textTokens = meaningfulTokens(text)
        return topicTokens.isNotEmpty() &&
            textTokens.isNotEmpty() &&
            topicTokens.any { topicToken ->
                textTokens.any { textToken ->
                    lexicallyRelated(topicToken, textToken)
                }
            }
    }

    private fun lexicallyRelated(
        left: String,
        right: String
    ): Boolean {
        if (left == right) return true
        if (left.length < 6 || right.length < 6) return false

        val limit = minOf(left.length, right.length)
        var commonPrefix = 0
        while (commonPrefix < limit && left[commonPrefix] == right[commonPrefix]) {
            commonPrefix++
        }
        return commonPrefix >= limit - 2 ||
            (
                commonPrefix >= 7 &&
                    left.length >= 9 &&
                    right.length >= 9
                )
    }

    private fun jsonHasKey(
        json: String,
        key: String
    ): Boolean =
        json.contains("\"$key\"")

    private fun extractTopic(originalText: String): String {
        val current = originalText
            .substringAfterLast("Pregunta actual:", originalText)
            .trim()
            .replace(
                Regex(
                    """^\s*(?:gamehub\s+ultra|gamehub|ultra)\s*[,;:.-]?\s*""",
                    RegexOption.IGNORE_CASE
                ),
                ""
            )
            .trim(' ', '¿', '?', '¡', '!')

        Regex(
            """^what\s+does\s+(.+?)\s+mean$""",
            RegexOption.IGNORE_CASE
        ).matchEntire(current)?.groupValues?.getOrNull(1)?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let { return it }

        return current
            .replace(
                Regex(
                    """^(?:h[aá]blame|cu[eé]ntame|dime(?:\s+algo)?|dime\s+qu[eé]\s+sabes|quiero\s+saber|quiero\s+que\s+me\s+hables|me\s+puedes\s+hablar|puedes\s+hablarme|podr[ií]as\s+hablarme|expl[ií]came(?:\s+algo)?|ens[eé][ñn]ame(?:\s+algo)?|qu[eé]\s+sabes|dame\s+informaci[oó]n|inf[oó]rmame)\s+(?:de|del|sobre|acerca\s+de)\s+""",
                    RegexOption.IGNORE_CASE
                ),
                ""
            )
            .replace(
                Regex(
                    """^(?:cual es el significado de|cuál es el significado de|que significa|qué significa|significado de|que es|qué es|que son|qué son|quien es|quién es|quienes son|quiénes son|define|definicion de|definición de|explicame|explícame|explica|dime que es|dime qué es|what is|what are|who is|who are|what does|meaning of|define)\s+""",
                    RegexOption.IGNORE_CASE
                ),
                ""
            )
            .trim()
            .replace(
                Regex(
                    """^(?:la|el)\s+marca\s+""",
                    RegexOption.IGNORE_CASE
                ),
                ""
            )
            .replace(
                Regex(
                    """^(?:un|una|unos|unas|ser|a|an)\s+""",
                    RegexOption.IGNORE_CASE
                ),
                ""
            )
            .trim()
    }

    private fun conciseIntro(value: String): String {
        val compact = value.replace(Regex("""\s+"""), " ").trim()
        if (compact.length <= MAX_DISPLAY_CHARS) return compact

        val candidate = compact.take(MAX_DISPLAY_CHARS)
        val sentenceEnd = maxOf(
            candidate.lastIndexOf(". "),
            candidate.lastIndexOf("? "),
            candidate.lastIndexOf("! ")
        )
        return if (sentenceEnd >= MAX_DISPLAY_CHARS / 2) {
            candidate.take(sentenceEnd + 1)
        } else {
            candidate.trimEnd() + "…"
        }
    }

    private fun slug(value: String): String =
        normalizeForComparison(value)
            .replace(Regex("""[^a-z0-9]+"""), "-")
            .trim('-')
            .take(120)

    private fun normalizeForComparison(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("""\p{M}+"""), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())
            .replace("+", "%20")

    private fun jsonString(
        json: String,
        key: String
    ): String? {
        val keyToken = "\"$key\""
        var searchFrom = 0

        while (searchFrom < json.length) {
            val keyIndex = json.indexOf(keyToken, searchFrom)
            if (keyIndex < 0) return null

            var index = keyIndex + keyToken.length
            while (index < json.length && json[index].isWhitespace()) index++
            if (index >= json.length || json[index] != ':') {
                searchFrom = keyIndex + keyToken.length
                continue
            }

            index++
            while (index < json.length && json[index].isWhitespace()) index++
            if (index >= json.length || json[index] != '"') {
                searchFrom = index.coerceAtLeast(keyIndex + 1)
                continue
            }

            index++
            val decoded = StringBuilder()
            while (index < json.length) {
                val ch = json[index]
                if (ch == '"') return decoded.toString()

                if (ch != '\\') {
                    decoded.append(ch)
                    index++
                    continue
                }

                if (index + 1 >= json.length) return null
                when (val escaped = json[index + 1]) {
                    '"' -> decoded.append('"')
                    '\\' -> decoded.append('\\')
                    '/' -> decoded.append('/')
                    'b' -> decoded.append('\b')
                    'f' -> decoded.append('\u000C')
                    'n' -> decoded.append('\n')
                    'r' -> decoded.append('\r')
                    't' -> decoded.append('\t')
                    'u' -> {
                        val hexStart = index + 2
                        val hexEnd = hexStart + 4
                        if (hexEnd > json.length) return null
                        val codePoint = json
                            .substring(hexStart, hexEnd)
                            .toIntOrNull(16)
                            ?: return null
                        decoded.append(codePoint.toChar())
                        index = hexEnd
                        continue
                    }

                    else -> decoded.append(escaped)
                }
                index += 2
            }

            return null
        }

        return null
    }

    private companion object {
        const val MAX_DISPLAY_CHARS = 1_800

        val TOPIC_STOP_WORDS = setOf(
            "una", "uno", "unos", "unas", "que", "del", "las", "los",
            "como", "para", "por", "con", "what", "who", "are", "the"
        )
    }
}
