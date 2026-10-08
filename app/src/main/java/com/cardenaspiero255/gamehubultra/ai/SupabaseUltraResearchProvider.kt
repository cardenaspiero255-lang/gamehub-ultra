package com.cardenaspiero255.gamehubultra.ai

import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

data class UltraResearchHttpResponse(
    val statusCode: Int,
    val body: String
)

interface UltraResearchBackendTransport {
    fun post(
        endpoint: String,
        apiKey: String,
        body: String,
        timeoutMillis: Long
    ): String

    fun postResponse(
        endpoint: String,
        apiKey: String,
        body: String,
        timeoutMillis: Long
    ): UltraResearchHttpResponse =
        UltraResearchHttpResponse(
            statusCode = 200,
            body = post(
                endpoint = endpoint,
                apiKey = apiKey,
                body = body,
                timeoutMillis = timeoutMillis
            )
        )

    fun cancelRequest(worker: Thread) = Unit
}

object HttpUrlConnectionUltraResearchTransport : UltraResearchBackendTransport {
    private val activeConnections =
        ConcurrentHashMap<Thread, HttpURLConnection>()

    override fun post(
        endpoint: String,
        apiKey: String,
        body: String,
        timeoutMillis: Long
    ): String {
        val httpResponse = postResponse(
            endpoint = endpoint,
            apiKey = apiKey,
            body = body,
            timeoutMillis = timeoutMillis
        )
        if (httpResponse.statusCode !in 200..299) {
            error("Research backend HTTP ${httpResponse.statusCode}")
        }
        return httpResponse.body
    }

    override fun postResponse(
        endpoint: String,
        apiKey: String,
        body: String,
        timeoutMillis: Long
    ): UltraResearchHttpResponse {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        val worker = Thread.currentThread()
        activeConnections[worker] = connection
        val safeTimeout = timeoutMillis.coerceIn(1_000L, 60_000L).toInt()
        connection.requestMethod = "POST"
        connection.connectTimeout = safeTimeout
        connection.readTimeout = safeTimeout
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("apikey", apiKey)
        connection.setRequestProperty("Accept", "application/json")

        return try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(body)
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            UltraResearchHttpResponse(
                statusCode = status,
                body = stream
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    .orEmpty()
            )
        } finally {
            activeConnections.remove(worker, connection)
            connection.disconnect()
        }
    }

    override fun cancelRequest(worker: Thread) {
        activeConnections.remove(worker)?.disconnect()
    }
}

class SupabaseUltraResearchProvider(
    private val supabaseUrl: String,
    private val publishableKey: String,
    private val transport: UltraResearchBackendTransport =
        HttpUrlConnectionUltraResearchTransport,
    private val requiredEngineVersion: String? = null
) : UltraResearchProvider {
    override val id: String = "supabase-ultra-research"

    override fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence =
        when (val result = fetchResult(request)) {
            is UltraProviderResult.Evidence -> result.evidence
            is UltraProviderResult.Abstained ->
                error(result.message ?: "Research backend abstained: ${result.reasonCode}")
            is UltraProviderResult.Failure ->
                error(result.message ?: "Research backend failed: ${result.reasonCode}")
        }

    override fun cancelActiveRequest(worker: Thread) {
        transport.cancelRequest(worker)
    }

    override fun fetchResult(
        request: UltraGeneralQueryRequest
    ): UltraProviderResult {
        if (supabaseUrl.isBlank() || publishableKey.isBlank()) {
            return UltraProviderResult.Failure(
                reasonCode = "BACKEND_NOT_CONFIGURED",
                message = "El backend de investigación no está configurado.",
                retryable = false
            )
        }

        val endpoint = supabaseUrl.trimEnd('/') + "/functions/v1/ultra-research"
        val httpResponse = try {
            transport.postResponse(
                endpoint = endpoint,
                apiKey = publishableKey,
                body = UltraResearchJsonCodec.encodeRequest(request),
                timeoutMillis = request.timeoutMillis
            )
        } catch (error: Exception) {
            return UltraProviderResult.Failure(
                reasonCode = "BACKEND_NETWORK_FAILURE",
                message = error.message,
                retryable = true
            )
        }

        val decoded = UltraResearchJsonCodec.decodeResponse(httpResponse.body)
        val statusReason = reasonCodeForStatus(httpResponse.statusCode)

        if (httpResponse.statusCode in 200..299 && requiredEngineVersion != null) {
            val backendVersion = decoded.engineVersion?.trim()
            if (backendVersion != requiredEngineVersion) {
                return UltraProviderResult.Failure(
                    reasonCode = "BACKEND_VERSION_MISMATCH",
                    message =
                        "Ultra Research requiere V$requiredEngineVersion pero el backend respondió " +
                            (backendVersion?.let { "V$it" } ?: "sin versión"),
                    retryable = false,
                    stage = "protocol"
                )
            }
        }

        if (decoded.abstained) {
            return UltraProviderResult.Abstained(
                reasonCode = decoded.reasonCode ?: statusReason ?: "BACKEND_ABSTAINED",
                message = decoded.message,
                retryable = decoded.retryable ||
                    httpResponse.statusCode in RETRYABLE_HTTP_STATUSES,
                stage = decoded.stage,
                upstreamStatus = decoded.upstreamStatus
                    ?: httpResponse.statusCode.takeIf { it !in 200..299 },
                sources = decoded.sourceIds
            )
        }

        if (httpResponse.statusCode !in 200..299) {
            return UltraProviderResult.Failure(
                reasonCode = decoded.reasonCode ?: statusReason ?: "BACKEND_HTTP_FAILURE",
                message = decoded.message ?: "Research backend HTTP ${httpResponse.statusCode}",
                retryable = decoded.retryable ||
                    httpResponse.statusCode in RETRYABLE_HTTP_STATUSES,
                stage = decoded.stage,
                upstreamStatus = decoded.upstreamStatus ?: httpResponse.statusCode
            )
        }

        val claimKey = decoded.claimKey.orEmpty().trim()
        val value = decoded.value.orEmpty().trim()
        val displayText = decoded.displayText.orEmpty().trim()
        val sourceId = decoded.sourceId.orEmpty().trim()
        // Offline stable knowledge is allowed to be informative without a
        // source, but must never impersonate externally verified evidence.
        val unsourcedLocal =
            sourceId.isBlank() &&
                !decoded.authoritative &&
                decoded.independentSourceCount == 0 &&
                decoded.sourceIds.isEmpty() &&
                claimKey.startsWith("local-")
        if (
            claimKey.isBlank() ||
            value.isBlank() ||
            displayText.isBlank() ||
            (sourceId.isBlank() && !unsourcedLocal)
        ) {
            return UltraProviderResult.Failure(
                reasonCode = "INVALID_BACKEND_RESPONSE",
                message = "La respuesta del backend está incompleta.",
                retryable = false,
                stage = decoded.stage
            )
        }

        return UltraProviderResult.Evidence(
            UltraResearchEvidence(
                claimKey = claimKey,
                value = value,
                displayText = displayText,
                sourceId = sourceId,
                supportingSourceIds = decoded.sourceIds
                    .map(String::trim)
                    .filter { it.isNotBlank() && it != sourceId }
                    .distinct(),
                independentSourceCount = if (unsourcedLocal) 0
                    else decoded.independentSourceCount.coerceAtLeast(1),
                authoritative = decoded.authoritative
            )
        )
    }

    private fun reasonCodeForStatus(status: Int): String? =
        when (status) {
            408, 504 -> "UPSTREAM_TIMEOUT"
            429 -> "UPSTREAM_RATE_LIMIT"
            500, 502, 503 -> "UPSTREAM_UNAVAILABLE"
            else -> null
        }

    private companion object {
        val RETRYABLE_HTTP_STATUSES = setOf(408, 429, 500, 502, 503, 504)
    }
}
