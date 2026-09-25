package com.cardenaspiero255.gamehubultra.ai

import java.text.Normalizer
import java.util.Locale
import java.util.UUID

interface UltraMemoryPersistence {
    fun read(): String?
    fun write(serialized: String)
    fun clear()
}

class UltraMemoryRepository(
    private val persistence: UltraMemoryPersistence,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
    maxRecords: Int = 500
) : UltraLongTermMemoryGateway {
    private val lock = Any()
    private val safeMaxRecords = maxRecords.coerceIn(20, 2_000)

    private var state: UltraMemorySnapshot = persistence.read()
        ?.takeIf { it.startsWith("ULTRA_MEMORY_V1|") }
        ?.let(UltraMemoryCodec::decode)
        ?: UltraMemorySnapshot()

    fun snapshot(): UltraMemorySnapshot = synchronized(lock) {
        state.copy(records = state.records.toList())
    }

    override fun handleCommand(
        message: String,
        scope: UltraMemoryScope
    ): String? {
        val command = UltraMemoryCommandParser.parse(message) ?: return null

        return synchronized(lock) {
            when (command) {
                UltraMemoryCommand.Enable -> {
                    persist(state.copy(enabled = true))
                    "Memoria de Ultra activada."
                }

                UltraMemoryCommand.Disable -> {
                    persist(state.copy(enabled = false))
                    "Memoria de Ultra desactivada. No guardaré nuevas conversaciones hasta que la actives."
                }

                is UltraMemoryCommand.Remember -> remember(command.fact, scope)

                is UltraMemoryCommand.Forget -> {
                    val removed = removeMatching(command.query, scope)
                    if (removed > 0) {
                        "Olvidé $removed recuerdo(s) relacionado(s)."
                    } else {
                        "No encontré un recuerdo relacionado para olvidar."
                    }
                }

                is UltraMemoryCommand.Delete -> {
                    val removed = removeMatching(command.query, scope)
                    if (removed > 0) {
                        "Eliminé $removed recuerdo(s) relacionado(s)."
                    } else {
                        "No encontré un recuerdo relacionado para eliminar."
                    }
                }

                is UltraMemoryCommand.Pin -> {
                    val changed = mutateMatching(command.query, scope) {
                        it.copy(pinned = true, archived = false)
                    }
                    if (changed > 0) {
                        "Fijé $changed recuerdo(s)."
                    } else {
                        "No encontré un recuerdo relacionado para fijar."
                    }
                }

                is UltraMemoryCommand.Archive -> {
                    val changed = mutateMatching(command.query, scope) {
                        it.copy(archived = true)
                    }
                    if (changed > 0) {
                        "Archivé $changed recuerdo(s)."
                    } else {
                        "No encontré un recuerdo relacionado para archivar."
                    }
                }

                UltraMemoryCommand.ClearHistory -> {
                    clearConversationHistoryLocked(scope.userId)
                    "Historial de conversación borrado. Los recuerdos que pediste guardar explícitamente se mantienen."
                }

                UltraMemoryCommand.ClearAll -> {
                    val next = state.copy(
                        records = state.records.filterNot {
                            it.scope.userId == scope.userId
                        }
                    )
                    persist(next)
                    "Borré la memoria guardada de Ultra para este usuario."
                }
            }
        }
    }

    override fun recallContext(
        message: String,
        scope: UltraMemoryScope,
        limit: Int
    ): List<UltraMemoryRecall> = synchronized(lock) {
        if (!state.enabled) return@synchronized emptyList()
        UltraMemoryRetrieval.relevant(
            query = message,
            records = state.records,
            scope = scope,
            limit = limit
        )
    }

    fun syncConversation(
        previous: List<String>,
        next: List<String>,
        scope: UltraMemoryScope,
        threadId: String = UltraStoredMemory.DEFAULT_THREAD_ID,
        timestampMillis: Long? = null
    ) {
        val newEntries = UltraConversationDelta.newEntries(previous, next)
        if (newEntries.isEmpty()) return

        synchronized(lock) {
            if (!state.enabled) return@synchronized

            val baseTimestamp = timestampMillis ?: nowMillis()
            val additions = newEntries.mapIndexedNotNull { index, entry ->
                val parsed = parseConversationEntry(entry) ?: return@mapIndexedNotNull null
                if (!UltraMemorySafety.canPersist(parsed.second)) {
                    return@mapIndexedNotNull null
                }
                UltraStoredMemory(
                    id = idFactory(),
                    kind = UltraMemoryKind.CONVERSATION,
                    role = parsed.first,
                    text = parsed.second,
                    timestampMillis = baseTimestamp + index,
                    scope = scope,
                    threadId = threadId
                )
            }

            if (additions.isNotEmpty()) {
                persist(
                    state.copy(
                        records = trimRecords(state.records + additions)
                    )
                )
            }
        }
    }

    fun recentConversationLines(
        limit: Int,
        scope: UltraMemoryScope = UltraMemoryScope()
    ): List<String> = synchronized(lock) {
        state.records.asSequence()
            .filter { it.scope.userId == scope.userId }
            .filter {
                it.scope.gamePackage == null ||
                    it.scope.gamePackage == scope.gamePackage
            }
            .filterNot { it.archived }
            .filter { it.kind == UltraMemoryKind.CONVERSATION }
            .takeLastCompat(limit.coerceIn(1, 100))
            .map(::formatConversationEntry)
            .toList()
    }

    fun clearConversationHistory(userId: String = "local") {
        synchronized(lock) {
            clearConversationHistoryLocked(userId)
        }
    }

    fun exportSnapshot(): String = synchronized(lock) {
        UltraMemoryCodec.encode(state)
    }

    fun importSnapshot(serialized: String): Boolean {
        if (!serialized.startsWith("ULTRA_MEMORY_V1|")) return false
        val decoded = UltraMemoryCodec.decode(serialized)
        if (decoded.records.any { !UltraMemorySafety.canPersist(it.text) }) {
            return false
        }

        synchronized(lock) {
            persist(
                decoded.copy(
                    records = trimRecords(decoded.records)
                )
            )
        }
        return true
    }

    fun search(
        query: String,
        scope: UltraMemoryScope,
        limit: Int = 20
    ): List<UltraMemoryRecall> = synchronized(lock) {
        UltraMemoryRetrieval.relevant(
            query = query,
            records = state.records,
            scope = scope,
            limit = limit
        )
    }

    private fun remember(fact: String, scope: UltraMemoryScope): String {
        val clean = fact.trim()
        if (!state.enabled) {
            return "La memoria está desactivada. Actívala antes de pedirme que recuerde algo."
        }
        if (!UltraMemorySafety.canPersist(clean)) {
            return "No guardaré ese dato porque parece contener una contraseña, token, clave o credencial sensible."
        }

        val duplicate = state.records.any {
            it.kind == UltraMemoryKind.FACT &&
                it.scope == scope &&
                normalize(it.text) == normalize(clean) &&
                !it.archived
        }
        if (duplicate) {
            return "Ya recordaba eso."
        }

        val record = UltraStoredMemory(
            id = idFactory(),
            kind = UltraMemoryKind.FACT,
            role = UltraMemoryRole.SYSTEM,
            text = clean,
            timestampMillis = nowMillis(),
            scope = scope
        )
        persist(
            state.copy(
                records = trimRecords(state.records + record)
            )
        )
        return "Lo recordaré: $clean"
    }

    private fun clearConversationHistoryLocked(userId: String) {
        persist(
            state.copy(
                records = state.records.filter {
                    it.scope.userId != userId ||
                        it.kind == UltraMemoryKind.FACT
                }
            )
        )
    }

    private fun removeMatching(query: String, scope: UltraMemoryScope): Int {
        val ids = matchingIds(query, scope, includeArchived = true)
        if (ids.isEmpty()) return 0
        persist(
            state.copy(
                records = state.records.filterNot { it.id in ids }
            )
        )
        return ids.size
    }

    private fun mutateMatching(
        query: String,
        scope: UltraMemoryScope,
        transform: (UltraStoredMemory) -> UltraStoredMemory
    ): Int {
        val ids = matchingIds(query, scope)
        if (ids.isEmpty()) return 0
        persist(
            state.copy(
                records = state.records.map { record ->
                    if (record.id in ids) transform(record) else record
                }
            )
        )
        return ids.size
    }

    private fun matchingIds(
        query: String,
        scope: UltraMemoryScope,
        includeArchived: Boolean = false
    ): Set<String> {
        val stopWords = setOf(
            "a", "al", "de", "del", "el", "en", "la", "las", "lo", "los",
            "me", "mi", "para", "por", "que", "un", "una", "y",
            "and", "for", "my", "of", "the", "to"
        )
        val queryTokens = normalize(query)
            .split(' ')
            .filter { it.length >= 2 && it !in stopWords }
            .toSet()
        if (queryTokens.isEmpty()) return emptySet()

        val scoped = state.records.asSequence()
            .filter { it.scope.userId == scope.userId }
            .filter {
                it.scope.gamePackage == null ||
                    it.scope.gamePackage == scope.gamePackage
            }
            .filter { includeArchived || !it.archived }
            .toList()

        fun matches(record: UltraStoredMemory): Boolean {
            val recordTokens = normalize(record.text)
                .split(' ')
                .filter { it.length >= 2 }
                .toSet()
            return recordTokens.containsAll(queryTokens)
        }

        return scoped
            .filter(::matches)
            .mapTo(linkedSetOf()) { it.id }
    }

    private fun parseConversationEntry(
        entry: String
    ): Pair<UltraMemoryRole, String>? {
        val clean = entry.trim()
        if (clean.isBlank()) return null

        return when {
            clean.startsWith("Tú:", ignoreCase = true) ||
                clean.startsWith("Tu:", ignoreCase = true) ->
                UltraMemoryRole.USER to clean.substringAfter(':').trim()

            clean.startsWith("Ultra:", ignoreCase = true) ->
                UltraMemoryRole.ASSISTANT to clean.substringAfter(':').trim()

            else ->
                UltraMemoryRole.SYSTEM to clean
        }.takeIf { it.second.isNotBlank() }
    }

    private fun formatConversationEntry(record: UltraStoredMemory): String =
        when (record.role) {
            UltraMemoryRole.USER -> "Tú: ${record.text}"
            UltraMemoryRole.ASSISTANT -> "Ultra: ${record.text}"
            UltraMemoryRole.SYSTEM -> record.text
        }

    private fun trimRecords(records: List<UltraStoredMemory>): List<UltraStoredMemory> {
        if (records.size <= safeMaxRecords) return records

        val protected = records
            .filter { it.pinned || it.kind == UltraMemoryKind.FACT }
            .takeLast(safeMaxRecords)
        val protectedIds = protected.mapTo(hashSetOf()) { it.id }
        val remainingCapacity = (safeMaxRecords - protected.size).coerceAtLeast(0)
        val recent = records
            .filterNot { it.id in protectedIds }
            .takeLast(remainingCapacity)
        val keepIds = (protected + recent).mapTo(hashSetOf()) { it.id }
        return records.filter { it.id in keepIds }
    }

    private fun persist(next: UltraMemorySnapshot) {
        val serialized = UltraMemoryCodec.encode(next)
        persistence.write(serialized)
        state = next
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("""\p{M}+"""), "")
            .replace(Regex("""[^a-z0-9 ]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun <T> Sequence<T>.takeLastCompat(limit: Int): Sequence<T> =
        toList().takeLast(limit).asSequence()
}
