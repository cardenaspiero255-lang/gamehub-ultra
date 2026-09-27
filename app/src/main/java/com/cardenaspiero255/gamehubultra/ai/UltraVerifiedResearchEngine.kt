package com.cardenaspiero255.gamehubultra.ai

import java.util.Locale
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

interface UltraResearchProvider {
    val id: String
    fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence
}

data class UltraResearchEvidence(
    val claimKey: String,
    val value: String,
    val displayText: String,
    val sourceId: String,
    val supportingSourceIds: List<String> = emptyList(),
    val independentSourceCount: Int = 1,
    val authoritative: Boolean = false
)

enum class UltraAnswerConfidence {
    HIGH,
    MEDIUM,
    LOW
}

data class UltraVerifiedResearchResult(
    val message: String,
    val confidence: UltraAnswerConfidence,
    val sources: List<String> = emptyList(),
    val abstained: Boolean,
    val fromCache: Boolean = false,
    val timedOut: Boolean = false,
    val fallbackUsed: Boolean = false,
    val sensitiveInputBlocked: Boolean = false
)

class UltraResearchCache {
    companion object {
        const val CURRENT_DATA_TTL_MS = 5 * 60 * 1000L
        const val COMPARISON_TTL_MS = 60 * 60 * 1000L
        const val GENERAL_KNOWLEDGE_TTL_MS = 24 * 60 * 60 * 1000L
    }

    private data class Entry(
        val result: UltraVerifiedResearchResult,
        val expiresAtMillis: Long
    )

    private val entries = LinkedHashMap<String, Entry>()

    @Synchronized
    fun get(key: String, nowMillis: Long): UltraVerifiedResearchResult? {
        val entry = entries[key] ?: return null
        if (nowMillis >= entry.expiresAtMillis) {
            entries.remove(key)
            return null
        }
        return entry.result.copy(fromCache = true)
    }

    @Synchronized
    fun put(
        key: String,
        result: UltraVerifiedResearchResult,
        expiresAtMillis: Long
    ) {
        entries[key] = Entry(
            result = result.copy(fromCache = false),
            expiresAtMillis = expiresAtMillis
        )
        while (entries.size > 64) {
            val oldest = entries.entries.firstOrNull()?.key ?: break
            entries.remove(oldest)
        }
    }
}

object UltraSensitiveInputGuard {
    private val jwtPattern = Regex(
        """\beyJ[A-Za-z0-9_-]{5,}\.[A-Za-z0-9_-]{5,}\.[A-Za-z0-9_-]{5,}\b"""
    )
    private val assignedSecretPattern = Regex(
        """(?i)\b(api[_-]?key|token|password|passwd|secret|clave)\s*[:=]\s*\S+"""
    )
    private val commonSecretPrefixPattern = Regex(
        """(?i)\b(sk-[A-Za-z0-9_-]{12,}|gh[pousr]_[A-Za-z0-9_]{20,})\b"""
    )

    fun containsSensitiveMaterial(text: String): Boolean =
        jwtPattern.containsMatchIn(text) ||
            assignedSecretPattern.containsMatchIn(text) ||
            commonSecretPrefixPattern.containsMatchIn(text)
}

class UltraVerifiedResearchEngine(
    private val providers: List<UltraResearchProvider>,
    private val cache: UltraResearchCache = UltraResearchCache(),
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val executor: ExecutorService = Executors.newFixedThreadPool(
        providers.size.coerceIn(1, 4)
    )
) : AutoCloseable {

    private data class ProviderAttempt(
        val index: Int,
        val providerId: String,
        val evidence: UltraResearchEvidence?,
        val failed: Boolean
    )

    fun answer(request: UltraGeneralQueryRequest): UltraVerifiedResearchResult {
        if (UltraSensitiveInputGuard.containsSensitiveMaterial(request.originalText)) {
            return abstention(
                timedOut = false,
                fallbackUsed = false,
                sensitiveInputBlocked = true
            )
        }

        val key = cacheKey(request)
        cache.get(key, nowMillis())?.let { return it }

        if (providers.isEmpty()) {
            return abstention(
                timedOut = false,
                fallbackUsed = false
            )
        }

        val completion = ExecutorCompletionService<ProviderAttempt>(executor)
        val submitted = providers.mapIndexed { index, provider ->
            completion.submit {
                runCatching {
                    ProviderAttempt(
                        index = index,
                        providerId = provider.id,
                        evidence = provider.fetch(request),
                        failed = false
                    )
                }.getOrElse {
                    ProviderAttempt(
                        index = index,
                        providerId = provider.id,
                        evidence = null,
                        failed = true
                    )
                }
            }
        }

        val attempts = mutableListOf<ProviderAttempt>()
        val timeoutNanos = TimeUnit.MILLISECONDS.toNanos(
            request.timeoutMillis.coerceAtLeast(1L)
        )
        val deadline = System.nanoTime() + timeoutNanos
        var timedOut = false

        repeat(providers.size) {
            val remaining = deadline - System.nanoTime()
            if (remaining <= 0L) {
                timedOut = true
                return@repeat
            }

            val completed = completion.poll(remaining, TimeUnit.NANOSECONDS)
            if (completed == null) {
                timedOut = true
                return@repeat
            }
            attempts += runCatching { completed.get() }.getOrElse {
                ProviderAttempt(
                    index = -1,
                    providerId = "unknown",
                    evidence = null,
                    failed = true
                )
            }
        }

        if (timedOut) {
            submitted.forEach { future ->
                if (!future.isDone) future.cancel(true)
            }
        }

        val evidenceAttempts = attempts.filter { it.evidence != null }
        val primarySucceeded = attempts.any {
            it.index == 0 && it.evidence != null
        }
        val fallbackUsed = !primarySucceeded &&
            evidenceAttempts.any { it.index > 0 }

        if (evidenceAttempts.isEmpty()) {
            return abstention(
                timedOut = timedOut,
                fallbackUsed = fallbackUsed
            )
        }

        val dominantClaim = evidenceAttempts
            .groupBy { it.evidence!!.claimKey.trim().lowercase(Locale.ROOT) }
            .maxByOrNull { (_, group) -> group.size }
            ?.value
            .orEmpty()

        val values = dominantClaim.groupBy {
            it.evidence!!.value.trim().lowercase(Locale.ROOT)
        }

        if (values.size != 1) {
            return abstention(
                timedOut = timedOut,
                fallbackUsed = fallbackUsed,
                sources = dominantClaim
                    .flatMap { attempt -> attempt.evidence?.allSourceIds().orEmpty() }
                    .distinct()
            )
        }

        val agreeing = values.values.single()
        val evidence = agreeing.first().evidence!!
        val sources = agreeing
            .flatMap { attempt -> attempt.evidence?.allSourceIds().orEmpty() }
            .distinct()
        val corroborationCount = maxOf(
            agreeing.size,
            agreeing.maxOfOrNull {
                it.evidence?.independentSourceCount?.coerceAtLeast(1) ?: 1
            } ?: 1
        )
        val confidence = when {
            corroborationCount >= 2 -> UltraAnswerConfidence.HIGH
            evidence.authoritative -> UltraAnswerConfidence.MEDIUM
            else -> UltraAnswerConfidence.LOW
        }

        if (
            confidence == UltraAnswerConfidence.LOW &&
            request.kind != UltraGeneralQueryKind.GENERAL_KNOWLEDGE
        ) {
            return abstention(
                timedOut = timedOut,
                fallbackUsed = fallbackUsed,
                sources = sources
            )
        }

        val result = UltraVerifiedResearchResult(
            message = evidence.displayText,
            confidence = confidence,
            sources = sources,
            abstained = false,
            timedOut = timedOut,
            fallbackUsed = fallbackUsed
        )

        cache.put(
            key = key,
            result = result,
            expiresAtMillis = nowMillis() + ttlMillis(request.kind)
        )
        return result
    }

    private fun abstention(
        timedOut: Boolean,
        fallbackUsed: Boolean,
        sensitiveInputBlocked: Boolean = false,
        sources: List<String> = emptyList()
    ): UltraVerifiedResearchResult =
        UltraVerifiedResearchResult(
            message = if (sensitiveInputBlocked) {
                "No enviaré secretos, tokens ni credenciales a proveedores externos."
            } else {
                "No pude verificarlo con suficiente confianza."
            },
            confidence = UltraAnswerConfidence.LOW,
            sources = sources,
            abstained = true,
            timedOut = timedOut,
            fallbackUsed = fallbackUsed,
            sensitiveInputBlocked = sensitiveInputBlocked
        )

    private fun UltraResearchEvidence.allSourceIds(): List<String> =
        (listOf(sourceId) + supportingSourceIds)
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()

    private fun cacheKey(request: UltraGeneralQueryRequest): String =
        request.kind.name + ":" +
            request.originalText
                .lowercase(Locale.ROOT)
                .replace(Regex("""\s+"""), " ")
                .trim()

    private fun ttlMillis(kind: UltraGeneralQueryKind): Long =
        when (kind) {
            UltraGeneralQueryKind.CURRENT_DATA ->
                UltraResearchCache.CURRENT_DATA_TTL_MS
            UltraGeneralQueryKind.COMPARISON_RESEARCH ->
                UltraResearchCache.COMPARISON_TTL_MS
            UltraGeneralQueryKind.GENERAL_KNOWLEDGE ->
                UltraResearchCache.GENERAL_KNOWLEDGE_TTL_MS
        }

    override fun close() {
        executor.shutdownNow()
    }
}
