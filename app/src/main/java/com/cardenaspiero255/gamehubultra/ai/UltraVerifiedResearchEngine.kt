package com.cardenaspiero255.gamehubultra.ai

import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

sealed interface UltraProviderResult {
    data class Evidence(
        val evidence: UltraResearchEvidence
    ) : UltraProviderResult

    data class Abstained(
        val reasonCode: String,
        val message: String? = null,
        val retryable: Boolean = false,
        val stage: String? = null,
        val upstreamStatus: Int? = null,
        val sources: List<String> = emptyList()
    ) : UltraProviderResult

    data class Failure(
        val reasonCode: String,
        val message: String? = null,
        val retryable: Boolean = false,
        val stage: String? = null,
        val upstreamStatus: Int? = null
    ) : UltraProviderResult
}

interface UltraResearchProvider {
    val id: String

    fun fetch(request: UltraGeneralQueryRequest): UltraResearchEvidence

    fun cancelActiveRequest(worker: Thread) = Unit

    fun fetchResult(request: UltraGeneralQueryRequest): UltraProviderResult =
        try {
            UltraProviderResult.Evidence(fetch(request))
        } catch (error: Exception) {
            UltraProviderResult.Failure(
                reasonCode = "PROVIDER_FAILURE",
                message = error.message,
                retryable = true
            )
        }
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
    val independentSourceCount: Int = 0,
    val abstained: Boolean,
    val fromCache: Boolean = false,
    val timedOut: Boolean = false,
    val fallbackUsed: Boolean = false,
    val sensitiveInputBlocked: Boolean = false,
    val reasonCode: String? = null,
    val retryable: Boolean = false,
    val stage: String? = null,
    val upstreamStatus: Int? = null
)

data class UltraResearchPersistentEntry(
    val result: UltraVerifiedResearchResult,
    val expiresAtMillis: Long
)

interface UltraResearchPersistentStore {
    fun read(key: String): UltraResearchPersistentEntry?
    fun write(key: String, entry: UltraResearchPersistentEntry)
    fun remove(key: String)
}

class UltraResearchCache {
    companion object {
        const val CURRENT_DATA_TTL_MS = 5 * 60 * 1000L
        const val COMPARISON_TTL_MS = 60 * 60 * 1000L
        const val GENERAL_KNOWLEDGE_TTL_MS = 30L * 24 * 60 * 60 * 1000L
        private const val MAX_ENTRIES = 256
    }

    private data class Entry(
        val result: UltraVerifiedResearchResult,
        val expiresAtMillis: Long
    )

    private val entries = LinkedHashMap<String, Entry>()

    @Volatile
    private var persistentStore: UltraResearchPersistentStore? = null

    @Synchronized
    fun attachPersistentStore(store: UltraResearchPersistentStore) {
        persistentStore = store
    }

    @Synchronized
    fun get(
        key: String,
        nowMillis: Long,
        allowPersistent: Boolean = false
    ): UltraVerifiedResearchResult? {
        entries[key]?.let { entry ->
            if (nowMillis < entry.expiresAtMillis) {
                return entry.result.copy(fromCache = true)
            }
            entries.remove(key)
        }

        if (!allowPersistent) return null
        val store = persistentStore ?: return null
        val persisted = runCatching { store.read(key) }.getOrNull() ?: return null
        if (nowMillis >= persisted.expiresAtMillis) {
            runCatching { store.remove(key) }
            return null
        }

        entries[key] = Entry(
            result = persisted.result.copy(fromCache = false),
            expiresAtMillis = persisted.expiresAtMillis
        )
        trimMemoryEntries()
        return persisted.result.copy(fromCache = true)
    }

    @Synchronized
    fun put(
        key: String,
        result: UltraVerifiedResearchResult,
        expiresAtMillis: Long,
        persist: Boolean = false
    ) {
        val cacheable = result.copy(fromCache = false)
        entries[key] = Entry(
            result = cacheable,
            expiresAtMillis = expiresAtMillis
        )
        trimMemoryEntries()

        if (
            persist &&
            !cacheable.abstained &&
            !cacheable.sensitiveInputBlocked &&
            cacheable.confidence != UltraAnswerConfidence.LOW
        ) {
            val store = persistentStore
            if (store != null) {
                runCatching {
                    store.write(
                        key = key,
                        entry = UltraResearchPersistentEntry(
                            result = cacheable,
                            expiresAtMillis = expiresAtMillis
                        )
                    )
                }
            }
        }
    }

    private fun trimMemoryEntries() {
        while (entries.size > MAX_ENTRIES) {
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
    private val providerHealth: UltraResearchProviderHealth =
        UltraResearchProviderHealth(),
    private val providerRanker: UltraAdaptiveProviderRanker =
        UltraAdaptiveProviderRanker(),
    private val consensusEngine: UltraWeightedConsensusEngine =
        UltraWeightedConsensusEngine()
) : UltraResearchGateway {

    private data class ProviderAttempt(
        val index: Int,
        val providerId: String,
        val result: UltraProviderResult,
        val latencyMillis: Long
    )

    override fun answer(request: UltraGeneralQueryRequest): UltraVerifiedResearchResult {
        if (UltraSensitiveInputGuard.containsSensitiveMaterial(request.originalText)) {
            return abstention(
                timedOut = false,
                fallbackUsed = false,
                sensitiveInputBlocked = true,
                reasonCode = "SENSITIVE_INPUT"
            )
        }

        val key = cacheKey(request)
        val usePersistentCache = shouldUsePersistentCache(request)
        cache.get(
            key = key,
            nowMillis = nowMillis(),
            allowPersistent = usePersistentCache
        )?.let { return it }

        if (providers.isEmpty()) {
            return abstention(
                timedOut = false,
                fallbackUsed = false,
                reasonCode = "NO_PROVIDERS"
            )
        }

        val domain = UltraFrontierDomainClassifier.classify(request)
        val healthyProviders = providerRanker.rank(
            providers = providers.filter { provider ->
                providerHealth.isAvailable(
                    providerId = provider.id,
                    nowMillis = nowMillis()
                )
            },
            domain = domain
        )
        if (healthyProviders.isEmpty()) {
            return abstention(
                timedOut = false,
                fallbackUsed = false,
                reasonCode = "PROVIDERS_COOLDOWN",
                retryable = false
            )
        }

        val partitionedProviders = healthyProviders
            .drop(request.researchProviderOffset)
        if (partitionedProviders.isEmpty()) {
            return abstention(
                timedOut = false,
                fallbackUsed = false,
                reasonCode = "PROVIDER_PARTITION_EMPTY",
                retryable = false
            )
        }

        val providerBudget = request.researchProviderBudget
            ?.coerceAtMost(partitionedProviders.size)
            ?: partitionedProviders.size
        val activeProviders = partitionedProviders.take(providerBudget)
        val requestExecutor = Executors.newFixedThreadPool(
            activeProviders.size.coerceIn(1, 4)
        )
        return try {
            answerWithProviders(
                request = request,
                key = key,
                usePersistentCache = usePersistentCache,
                requestExecutor = requestExecutor,
                activeProviders = activeProviders,
                domain = domain
            )
        } finally {
            requestExecutor.shutdownNow()
        }
    }

    private fun fetchProviderWithRetry(
        provider: UltraResearchProvider,
        request: UltraGeneralQueryRequest
    ): UltraProviderResult {
        var lastResult: UltraProviderResult = UltraProviderResult.Failure(
            reasonCode = "PROVIDER_FAILURE",
            retryable = true
        )

        repeat(MAX_PROVIDER_ATTEMPTS) { attemptIndex ->
            val result = try {
                provider.fetchResult(request)
            } catch (error: Exception) {
                UltraProviderResult.Failure(
                    reasonCode = "PROVIDER_FAILURE",
                    message = error.message,
                    retryable = true
                )
            }
            lastResult = result

            val retryable = when (result) {
                is UltraProviderResult.Failure -> result.retryable
                is UltraProviderResult.Abstained -> result.retryable
                is UltraProviderResult.Evidence -> false
            }
            if (!retryable || attemptIndex + 1 >= MAX_PROVIDER_ATTEMPTS) {
                return result
            }
        }

        return lastResult
    }

    private fun answerWithProviders(
        request: UltraGeneralQueryRequest,
        key: String,
        usePersistentCache: Boolean,
        requestExecutor: ExecutorService,
        activeProviders: List<UltraResearchProvider>,
        domain: UltraFrontierDomain
    ): UltraVerifiedResearchResult {
        val optionalStableKnowledge =
            request.kind == UltraGeneralQueryKind.GENERAL_KNOWLEDGE &&
                request.verificationMode == UltraVerificationMode.OPTIONAL &&
                !request.requiresFreshData

        val completion = ExecutorCompletionService<ProviderAttempt>(requestExecutor)
        val providerWorkers = activeProviders.map {
            AtomicReference<Thread?>(null)
        }
        val submitted = activeProviders.mapIndexed { index, provider ->
            completion.submit {
                val worker = Thread.currentThread()
                providerWorkers[index].set(worker)
                try {
                    val startedNanos = System.nanoTime()
                    val result = fetchProviderWithRetry(
                        provider = provider,
                        request = request
                    )
                    val latencyMillis = TimeUnit.NANOSECONDS.toMillis(
                        (System.nanoTime() - startedNanos).coerceAtLeast(0L)
                    )
                    ProviderAttempt(
                        index = index,
                        providerId = provider.id,
                        result = result,
                        latencyMillis = latencyMillis
                    )
                } finally {
                    providerWorkers[index].compareAndSet(worker, null)
                }
            }
        }

        val attempts = mutableListOf<ProviderAttempt>()
        val timeoutNanos = TimeUnit.MILLISECONDS.toNanos(
            request.timeoutMillis.coerceAtLeast(1L)
        )
        val requestStartedNanos = System.nanoTime()
        val deadline = requestStartedNanos + timeoutNanos
        var timedOut = false
        var graceDeadlineNanos: Long? = null
        var stoppedAfterGrace = false

        try {
            while (attempts.size < activeProviders.size) {
                val now = System.nanoTime()
                val effectiveDeadline = minOf(
                    deadline,
                    graceDeadlineNanos ?: deadline
                )
                val remaining = effectiveDeadline - now

                if (remaining <= 0L) {
                    if (
                        graceDeadlineNanos != null &&
                        effectiveDeadline == graceDeadlineNanos &&
                        effectiveDeadline < deadline
                    ) {
                        stoppedAfterGrace = true
                    } else {
                        timedOut = true
                    }
                    break
                }

                val completed = completion.poll(
                    remaining,
                    TimeUnit.NANOSECONDS
                )
                if (completed == null) {
                    if (
                        graceDeadlineNanos != null &&
                        effectiveDeadline == graceDeadlineNanos &&
                        effectiveDeadline < deadline
                    ) {
                        stoppedAfterGrace = true
                    } else {
                        timedOut = true
                    }
                    break
                }

                val attempt = try {
                    completed.get()
                } catch (_: ExecutionException) {
                    ProviderAttempt(
                        index = -1,
                        providerId = "unknown",
                        result = UltraProviderResult.Failure(
                            reasonCode = "PROVIDER_EXECUTION_FAILURE",
                            retryable = true
                        ),
                        latencyMillis = 0L
                    )
                }
                attempts += attempt
                providerHealth.record(
                    providerId = attempt.providerId,
                    result = attempt.result,
                    nowMillis = nowMillis()
                )
                providerRanker.record(
                    providerId = attempt.providerId,
                    domain = domain,
                    result = attempt.result,
                    latencyMillis = attempt.latencyMillis
                )

                if (
                    optionalStableKnowledge &&
                    attempt.result is UltraProviderResult.Evidence &&
                    attempts.size < activeProviders.size &&
                    graceDeadlineNanos == null
                ) {
                    val graceDeadline = if (attempt.index == 0) {
                        val graceMillis = minOf(
                            PRIMARY_PROVIDER_GRACE_MS,
                            (request.timeoutMillis / 4L).coerceAtLeast(1L)
                        )
                        System.nanoTime() +
                            TimeUnit.MILLISECONDS.toNanos(graceMillis)
                    } else {
                        val primaryWaitMillis = minOf(
                            FALLBACK_FIRST_PRIMARY_WAIT_MS,
                            (request.timeoutMillis / 2L).coerceAtLeast(1L)
                        )
                        requestStartedNanos +
                            TimeUnit.MILLISECONDS.toNanos(primaryWaitMillis)
                    }
                    graceDeadlineNanos = minOf(
                        deadline,
                        graceDeadline
                    )
                }
            }
        } catch (interrupted: InterruptedException) {
            submitted.forEachIndexed { index, future ->
                if (!future.isDone) {
                    providerWorkers[index].get()?.let { worker ->
                        runCatching {
                            activeProviders[index].cancelActiveRequest(worker)
                        }
                    }
                    future.cancel(true)
                }
            }
            Thread.currentThread().interrupt()
            throw interrupted
        }

        if (timedOut || stoppedAfterGrace) {
            submitted.forEachIndexed { index, future ->
                if (!future.isDone) {
                    providerWorkers[index].get()?.let { worker ->
                        runCatching {
                            activeProviders[index].cancelActiveRequest(worker)
                        }
                    }
                    future.cancel(true)
                }
            }
        }

        val evidenceAttempts = attempts.mapNotNull { attempt ->
            val evidence = (attempt.result as? UltraProviderResult.Evidence)?.evidence
                ?: return@mapNotNull null
            attempt to evidence
        }
        val primarySucceeded = evidenceAttempts.any { (attempt, _) ->
            attempt.index == 0
        }
        val fallbackUsed = !primarySucceeded &&
            evidenceAttempts.any { (attempt, _) -> attempt.index > 0 }

        if (
            optionalStableKnowledge &&
            evidenceAttempts.isNotEmpty()
        ) {
            val stableEvidence = evidenceAttempts.map { it.second }
            val hasConflict = stableEvidence.indices.any { firstIndex ->
                ((firstIndex + 1) until stableEvidence.size).any { secondIndex ->
                    !stableKnowledgeEvidenceCompatible(
                        stableEvidence[firstIndex],
                        stableEvidence[secondIndex]
                    )
                }
            }

            if (hasConflict) {
                return abstention(
                    timedOut = false,
                    fallbackUsed = fallbackUsed,
                    sources = stableEvidence
                        .flatMap { it.allSourceIds() }
                        .distinct(),
                    reasonCode = "INSUFFICIENT_CORROBORATION"
                )
            }

            val (selectedAttempt, selectedEvidence) = evidenceAttempts
                .maxByOrNull { (attempt, evidence) ->
                    stableKnowledgeEvidenceScore(
                        providerIndex = attempt.index,
                        evidence = evidence
                    )
                }
                ?: error("Stable knowledge evidence unexpectedly disappeared")

            val sources = selectedEvidence.allSourceIds()
            // Multiple URLs can still belong to one underlying source. Confidence
            // must follow the provider's independent-source count, not URL count.
            val corroborationCount =
                selectedEvidence.independentSourceCount.coerceAtLeast(1)
            val confidence = when {
                corroborationCount >= 2 -> UltraAnswerConfidence.HIGH
                selectedEvidence.authoritative -> UltraAnswerConfidence.MEDIUM
                else -> UltraAnswerConfidence.LOW
            }
            val result = UltraVerifiedResearchResult(
                message = selectedEvidence.displayText,
                confidence = confidence,
                sources = sources,
                independentSourceCount = corroborationCount,
                abstained = false,
                timedOut = false,
                fallbackUsed = selectedAttempt.index > 0
            )

            val allProvidersSettled = attempts.size >= activeProviders.size
            if (allProvidersSettled) {
                cache.put(
                    key = key,
                    result = result,
                    expiresAtMillis = nowMillis() + ttlMillis(request),
                    persist = usePersistentCache &&
                        result.confidence != UltraAnswerConfidence.LOW
                )
            }
            return result
        }

        if (evidenceAttempts.isEmpty()) {
            val orderedAttempts = attempts.sortedBy { it.index }
            val orderedResults = orderedAttempts.map { it.result }
            val primaryAbstention = orderedAttempts
                .firstOrNull { it.index == 0 }
                ?.result
                ?.takeIf { result ->
                    result is UltraProviderResult.Abstained &&
                        result.reasonCode !in NON_ACTIONABLE_PUBLIC_FALLBACK_REASONS
                }
            val structuredIssue = primaryAbstention
                ?: orderedResults.firstOrNull {
                    it is UltraProviderResult.Failure
                }
                ?: orderedResults.firstOrNull { result ->
                    result is UltraProviderResult.Abstained &&
                        result.reasonCode !in NON_ACTIONABLE_PUBLIC_FALLBACK_REASONS
                }
                ?: if (!timedOut) {
                    orderedResults.firstOrNull {
                        it is UltraProviderResult.Abstained
                    }
                } else {
                    null
                }

            return when (structuredIssue) {
                is UltraProviderResult.Abstained ->
                    abstention(
                        timedOut = timedOut,
                        fallbackUsed = fallbackUsed,
                        reasonCode = structuredIssue.reasonCode,
                        retryable = structuredIssue.retryable,
                        stage = structuredIssue.stage,
                        upstreamStatus = structuredIssue.upstreamStatus,
                        sources = structuredIssue.sources
                    )

                is UltraProviderResult.Failure ->
                    abstention(
                        timedOut = timedOut,
                        fallbackUsed = fallbackUsed,
                        reasonCode = structuredIssue.reasonCode,
                        retryable = structuredIssue.retryable,
                        stage = structuredIssue.stage,
                        upstreamStatus = structuredIssue.upstreamStatus
                    )

                else ->
                    abstention(
                        timedOut = timedOut,
                        fallbackUsed = fallbackUsed,
                        reasonCode = if (timedOut) "UPSTREAM_TIMEOUT" else "NO_EVIDENCE",
                        retryable = timedOut
                    )
            }
        }

        val consensus = consensusEngine.decide(
            evidenceAttempts.map { (attempt, evidence) ->
                UltraWeightedEvidenceCandidate(
                    providerId = attempt.providerId,
                    claimKey = evidence.claimKey,
                    value = evidence.value,
                    displayText = evidence.displayText,
                    sourceIds = evidence.allSourceIds().toSet(),
                    independentSourceCount =
                        evidence.independentSourceCount.coerceAtLeast(1),
                    authoritative = evidence.authoritative,
                    providerScore = providerRanker.score(
                        attempt.providerId,
                        domain
                    )
                )
            }
        )

        if (!consensus.accepted) {
            return abstention(
                timedOut = timedOut,
                fallbackUsed = fallbackUsed,
                sources = evidenceAttempts
                    .flatMap { (_, evidence) -> evidence.allSourceIds() }
                    .distinct(),
                reasonCode = "INSUFFICIENT_CORROBORATION",
                retryable = activeProviders.size < providers.size
            )
        }

        val agreeing = evidenceAttempts.filter { (_, evidence) ->
            evidence.claimKey.trim().lowercase(Locale.ROOT) ==
                consensus.claimKey &&
                evidence.value.trim().lowercase(Locale.ROOT) ==
                    consensus.value
                        ?.trim()
                        ?.lowercase(Locale.ROOT)
        }
        val evidence = agreeing
            .maxByOrNull { (attempt, itemEvidence) ->
                providerRanker.score(attempt.providerId, domain) +
                    itemEvidence.independentSourceCount.coerceAtLeast(1)
            }
            ?.second
            ?: evidenceAttempts.first().second
        val sources = consensus.evidence
            .flatMap { it.sourceIds }
            .distinct()
        val corroborationCount =
            consensus.independentSourceCount.coerceAtLeast(1)
        val confidence = consensus.confidence

        val requiredGeneralKnowledge =
            request.kind == UltraGeneralQueryKind.GENERAL_KNOWLEDGE &&
                request.verificationMode == UltraVerificationMode.REQUIRED
        if (
            confidence == UltraAnswerConfidence.LOW &&
            (
                request.kind != UltraGeneralQueryKind.GENERAL_KNOWLEDGE ||
                    requiredGeneralKnowledge
                )
        ) {
            return abstention(
                timedOut = timedOut,
                fallbackUsed = fallbackUsed,
                sources = sources,
                reasonCode = "INSUFFICIENT_CORROBORATION",
                retryable =
                    requiredGeneralKnowledge &&
                        activeProviders.size < providers.size
            )
        }

        val result = UltraVerifiedResearchResult(
            message = consensus.displayText ?: evidence.displayText,
            confidence = confidence,
            sources = sources,
            independentSourceCount = corroborationCount,
            abstained = false,
            timedOut = timedOut,
            fallbackUsed = fallbackUsed
        )

        cache.put(
            key = key,
            result = result,
            expiresAtMillis = nowMillis() + ttlMillis(request),
            persist = usePersistentCache && confidence != UltraAnswerConfidence.LOW
        )
        return result

    }

    private fun abstention(
        timedOut: Boolean,
        fallbackUsed: Boolean,
        sensitiveInputBlocked: Boolean = false,
        sources: List<String> = emptyList(),
        reasonCode: String? = null,
        retryable: Boolean = false,
        stage: String? = null,
        upstreamStatus: Int? = null
    ): UltraVerifiedResearchResult =
        UltraVerifiedResearchResult(
            message = when {
                sensitiveInputBlocked ->
                    "No enviaré secretos, tokens ni credenciales a proveedores externos."
                timedOut || reasonCode == "UPSTREAM_TIMEOUT" ->
                    "La búsqueda tardó demasiado. Reintenta."
                reasonCode == "UPSTREAM_RATE_LIMIT" ->
                    "El servicio está ocupado. Prueba de nuevo."
                reasonCode == "UPSTREAM_UNAVAILABLE" ||
                    reasonCode == "BACKEND_FAILURE" ||
                    reasonCode == "BACKEND_HTTP_FAILURE" ||
                    reasonCode == "INVALID_BACKEND_RESPONSE" ||
                    reasonCode == "UPSTREAM_HTTP_ERROR" ||
                    reasonCode == "BACKEND_NETWORK_FAILURE" ||
                    reasonCode == "PROVIDER_FAILURE" ||
                    reasonCode == "PROVIDER_EXECUTION_FAILURE" ->
                    "No pude verificar la respuesta porque la conexión con las fuentes no está disponible. Revisa tu conexión e inténtalo de nuevo."
                reasonCode == "BACKEND_NOT_CONFIGURED" ||
                    reasonCode == "GENERAL_MODEL_NOT_CONFIGURED" ->
                    "El servicio de consulta todavía no está configurado para esa búsqueda."
                reasonCode == "PROVIDERS_COOLDOWN" ->
                    "Las fuentes disponibles están temporalmente en recuperación tras fallos repetidos. Inténtalo de nuevo en unos segundos."
                reasonCode == "PROVIDER_PARTITION_EMPTY" ->
                    "No quedan fuentes independientes en esta rama de investigación."
                sources.isEmpty() ->
                    "No encontré fuentes suficientes para confirmar ese dato."
                else ->
                    "Encontré información, pero falta corroboración suficiente para confirmar ese dato."
            },
            confidence = UltraAnswerConfidence.LOW,
            sources = sources,
            abstained = true,
            timedOut = timedOut,
            fallbackUsed = fallbackUsed,
            sensitiveInputBlocked = sensitiveInputBlocked,
            reasonCode = reasonCode,
            retryable = retryable,
            stage = stage,
            upstreamStatus = upstreamStatus
        )

    private fun UltraResearchEvidence.allSourceIds(): List<String> =
        (listOf(sourceId) + supportingSourceIds)
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()

    private fun stableKnowledgeEvidenceScore(
        providerIndex: Int,
        evidence: UltraResearchEvidence
    ): Int {
        val qualityTier = when {
            evidence.independentSourceCount >= 2 -> 3
            evidence.authoritative -> 2
            else -> 1
        }
        return qualityTier * 10_000 +
            evidence.independentSourceCount.coerceAtLeast(1) * 100 -
            providerIndex.coerceAtLeast(0)
    }

    private fun stableKnowledgeEvidenceCompatible(
        first: UltraResearchEvidence,
        second: UltraResearchEvidence
    ): Boolean {
        val firstText = first.displayText + " " + first.value
        val secondText = second.displayText + " " + second.value
        val firstTokens = stableKnowledgeTokens(firstText)
        val secondTokens = stableKnowledgeTokens(secondText)
        val firstNegatedPredicates = stableKnowledgeNegatedPredicates(firstText)
        val secondNegatedPredicates = stableKnowledgeNegatedPredicates(secondText)

        val firstContradictsSecond = firstNegatedPredicates.any { predicate ->
            predicate in secondTokens && predicate !in secondNegatedPredicates
        }
        val secondContradictsFirst = secondNegatedPredicates.any { predicate ->
            predicate in firstTokens && predicate !in firstNegatedPredicates
        }
        if (firstContradictsSecond || secondContradictsFirst) return false
        if (!stableKnowledgeNumericFactsCompatible(firstText, secondText)) {
            return false
        }

        val firstValue = normalizeStableText(first.value)
        val secondValue = normalizeStableText(second.value)
        if (firstValue.isNotBlank() && firstValue == secondValue) return true

        if (firstTokens.isEmpty() || secondTokens.isEmpty()) return false

        val overlap = firstTokens.intersect(secondTokens)
        if (overlap.size < 2) return false

        val smallerEvidence = minOf(
            firstTokens.size,
            secondTokens.size
        ).coerceAtLeast(1)
        return overlap.size.toDouble() / smallerEvidence.toDouble() >= 0.30
    }

    private fun stableKnowledgeNumericFactsCompatible(
        firstText: String,
        secondText: String
    ): Boolean {
        val firstFacts = stableKnowledgeNumericFacts(firstText)
        val secondFacts = stableKnowledgeNumericFacts(secondText)
        val sharedAnchors = firstFacts.keys.intersect(secondFacts.keys)

        return sharedAnchors.none { anchor ->
            val firstValues = firstFacts.getValue(anchor)
            val secondValues = secondFacts.getValue(anchor)
            val firstOnly = firstValues - secondValues
            val secondOnly = secondValues - firstValues
            firstOnly.isNotEmpty() && secondOnly.isNotEmpty()
        }
    }

    private fun stableKnowledgeNumericFacts(
        value: String
    ): Map<String, Set<String>> {
        val tokens = normalizeStableText(value)
            .replace(Regex("""[^a-z0-9.,%+-]+"""), " ")
            .split(' ')
            .map(String::trim)
            .filter(String::isNotBlank)
        val facts = linkedMapOf<String, MutableSet<String>>()

        tokens.forEachIndexed { index, token ->
            val number = stableKnowledgeQuantityValue(token)
                ?: return@forEachIndexed
            val precedingContext = tokens
                .subList(maxOf(0, index - 4), index)
                .map(::canonicalStableKnowledgeToken)
            if (precedingContext.any(STABLE_KNOWLEDGE_EDITORIAL_DATE_MARKERS::contains)) {
                return@forEachIndexed
            }
            val percentSuffix = if (token.endsWith("%")) "%" else ""
            val anchor = (
                tokens.drop(index + 1).asSequence() +
                    tokens.take(index).asReversed().asSequence()
                )
                .map(::canonicalStableKnowledgeToken)
                .firstOrNull { candidate ->
                    candidate.length >= 2 &&
                        candidate.none(Char::isDigit) &&
                        candidate !in STABLE_KNOWLEDGE_STOP_WORDS &&
                        candidate !in STABLE_KNOWLEDGE_NEGATION_FILLERS &&
                        candidate !in STABLE_KNOWLEDGE_ANCHOR_FUNCTION_WORDS
                }
                ?: return@forEachIndexed

            val predicate = tokens
                .take(index)
                .asReversed()
                .asSequence()
                .map(::canonicalStableKnowledgeToken)
                .firstOrNull { candidate ->
                    candidate.length >= 3 &&
                        candidate.none(Char::isDigit) &&
                        candidate != anchor &&
                        candidate !in STABLE_KNOWLEDGE_STOP_WORDS &&
                        candidate !in STABLE_KNOWLEDGE_NEGATION_FILLERS &&
                        candidate !in STABLE_KNOWLEDGE_ANCHOR_FUNCTION_WORDS &&
                        candidate !in STABLE_KNOWLEDGE_EDITORIAL_DATE_MARKERS
                }
            val factKey = if (predicate == null) {
                anchor
            } else {
                "$anchor|$predicate"
            }

            facts.getOrPut(factKey) { linkedSetOf() }
                .add(number + percentSuffix)
        }

        return facts.mapValues { (_, values) -> values.toSet() }
    }

    private fun stableKnowledgeQuantityValue(token: String): String? {
        val normalized = token
            .removeSuffix("%")
            .replace(',', '.')
        if (normalized.matches(Regex("""[+-]?\d+(?:\.\d+)?"""))) {
            return normalized.removePrefix("+")
        }
        return STABLE_KNOWLEDGE_WRITTEN_QUANTITIES[normalized]
    }

    private fun canonicalStableKnowledgeToken(value: String): String {
        val token = value.replace(Regex("""[^a-z0-9]+"""), "")
        return when {
            token.length > 5 && token.endsWith("es") -> token.dropLast(2)
            token.length > 4 && token.endsWith("s") -> token.dropLast(1)
            else -> token
        }
    }

    private fun stableKnowledgeNegatedPredicates(value: String): Set<String> {
        val normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("""\p{M}+"""), "")
            .lowercase(Locale.ROOT)

        return normalized
            .split(Regex("""[.!?;,:]+"""))
            .asSequence()
            .flatMap { clause ->
                val tokens = clause
                    .replace(Regex("""[^a-z0-9]+"""), " ")
                    .split(' ')
                    .map(String::trim)
                    .filter(String::isNotBlank)

                tokens.indices.asSequence()
                    .filter { index ->
                        tokens[index] in STABLE_KNOWLEDGE_NEGATIONS &&
                            tokens.getOrNull(index + 1) !in
                            STABLE_KNOWLEDGE_ADDITIVE_MARKERS
                    }
                    .mapNotNull { index ->
                        tokens
                            .drop(index + 1)
                            .firstOrNull { token ->
                                token.length >= 3 &&
                                    token !in STABLE_KNOWLEDGE_STOP_WORDS &&
                                    token !in STABLE_KNOWLEDGE_NEGATION_FILLERS
                            }
                    }
            }
            .toSet()
    }

    private fun stableKnowledgeTokens(value: String): Set<String> =
        normalizeStableText(value)
            .replace(Regex("""[^a-z0-9]+"""), " ")
            .split(' ')
            .asSequence()
            .map(String::trim)
            .filter { it.length >= 3 }
            .filterNot(STABLE_KNOWLEDGE_STOP_WORDS::contains)
            .toSet()

    private fun normalizeStableText(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("""\p{M}+"""), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun cacheKey(request: UltraGeneralQueryRequest): String =
        request.kind.name + ":" +
            request.verificationMode.name + ":" +
            (if (request.requiresFreshData) "fresh" else "stable") +
            (if (request.researchProviderOffset > 0) {
                ":partition=" + request.researchProviderOffset
            } else {
                ""
            }) + ":" +
            request.originalText
                .lowercase(Locale.ROOT)
                .replace(Regex("""\s+"""), " ")
                .trim()

    private fun shouldUsePersistentCache(
        request: UltraGeneralQueryRequest
    ): Boolean =
        request.kind == UltraGeneralQueryKind.GENERAL_KNOWLEDGE &&
            request.verificationMode == UltraVerificationMode.OPTIONAL &&
            !request.requiresFreshData

    private fun ttlMillis(request: UltraGeneralQueryRequest): Long =
        if (request.requiresFreshData) {
            UltraResearchCache.CURRENT_DATA_TTL_MS
        } else {
            when (request.kind) {
                UltraGeneralQueryKind.CURRENT_DATA ->
                    UltraResearchCache.CURRENT_DATA_TTL_MS
                UltraGeneralQueryKind.COMPARISON_RESEARCH ->
                    UltraResearchCache.COMPARISON_TTL_MS
                UltraGeneralQueryKind.GENERAL_KNOWLEDGE ->
                    UltraResearchCache.GENERAL_KNOWLEDGE_TTL_MS
            }
        }

    private companion object {
        const val MAX_PROVIDER_ATTEMPTS = 2
        const val PRIMARY_PROVIDER_GRACE_MS = 350L
        const val FALLBACK_FIRST_PRIMARY_WAIT_MS = 500L

        val STABLE_KNOWLEDGE_NEGATIONS = setOf(
            "no", "nunca", "jamas", "tampoco", "ni"
        )

        val STABLE_KNOWLEDGE_ADDITIVE_MARKERS = setOf(
            "solo", "solamente", "unicamente"
        )

        val STABLE_KNOWLEDGE_NEGATION_FILLERS = setOf(
            "esta", "estan", "este", "estos", "estas", "puede", "pueden",
            "debe", "deben", "suele", "suelen", "solo", "solamente",
            "son", "ser", "fue", "fueron", "era", "eran", "hay",
            "tiene", "tienen", "posee", "poseen"
        )

        val STABLE_KNOWLEDGE_ANCHOR_FUNCTION_WORDS = setOf(
            "de", "del", "en", "el", "la", "lo", "le", "y", "e", "o", "u",
            "a", "al", "es", "se", "su", "sus", "un", "mas", "entre", "hasta", "desde"
        )

        val STABLE_KNOWLEDGE_EDITORIAL_DATE_MARKERS = setOf(
            "actualizada", "actualizado", "revisada", "revisado", "publicada", "publicado",
            "editada", "editado", "modificada", "modificado", "consultada", "consultado"
        )

        val STABLE_KNOWLEDGE_WRITTEN_QUANTITIES = mapOf(
            "cero" to "0", "zero" to "0",
            "un" to "1", "una" to "1", "uno" to "1", "one" to "1",
            "dos" to "2", "two" to "2",
            "tres" to "3", "three" to "3",
            "cuatro" to "4", "four" to "4",
            "cinco" to "5", "five" to "5",
            "seis" to "6", "six" to "6",
            "siete" to "7", "seven" to "7",
            "ocho" to "8", "eight" to "8",
            "nueve" to "9", "nine" to "9",
            "diez" to "10", "ten" to "10",
            "once" to "11", "eleven" to "11",
            "doce" to "12", "twelve" to "12",
            "trece" to "13", "thirteen" to "13",
            "catorce" to "14", "fourteen" to "14",
            "quince" to "15", "fifteen" to "15",
            "dieciseis" to "16", "sixteen" to "16",
            "diecisiete" to "17", "seventeen" to "17",
            "dieciocho" to "18", "eighteen" to "18",
            "diecinueve" to "19", "nineteen" to "19",
            "veinte" to "20", "twenty" to "20"
        )

        val NON_ACTIONABLE_PUBLIC_FALLBACK_REASONS = setOf(
            "PUBLIC_FALLBACK_NOT_APPLICABLE",
            "PUBLIC_FALLBACK_CONTEXT_REQUIRED"
        )

        val STABLE_KNOWLEDGE_STOP_WORDS = setOf(
            "una", "uno", "unos", "unas", "que", "del", "las", "los",
            "con", "para", "por", "como", "capaz", "puede", "ser",
            "the", "and", "with", "that", "this", "from", "into",
            "are", "was", "were", "has", "have"
        )
    }

    override fun close() = Unit
}
