package com.cardenaspiero255.gamehubultra.ai

import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.util.Base64
import java.util.Locale
import kotlin.math.max

data class UltraMemoryScope(
    val userId: String = "local",
    val gamePackage: String? = null
)

enum class UltraMemoryKind {
    CONVERSATION,
    FACT,
    SUMMARY
}

enum class UltraMemoryRole {
    USER,
    ASSISTANT,
    SYSTEM
}

data class UltraStoredMemory(
    val id: String,
    val kind: UltraMemoryKind,
    val role: UltraMemoryRole,
    val text: String,
    val timestampMillis: Long,
    val scope: UltraMemoryScope,
    val threadId: String = DEFAULT_THREAD_ID,
    val pinned: Boolean = false,
    val archived: Boolean = false
) {
    companion object {
        const val DEFAULT_THREAD_ID = "default"
    }
}

data class UltraMemorySnapshot(
    val enabled: Boolean = true,
    val records: List<UltraStoredMemory> = emptyList()
)

enum class UltraMemoryProvenance {
    REMEMBERED_FACT,
    PRIOR_CONVERSATION,
    SUMMARY
}

data class UltraMemoryRecall(
    val record: UltraStoredMemory,
    val score: Double,
    val provenance: UltraMemoryProvenance
)

sealed interface UltraMemoryCommand {
    data class Remember(val fact: String) : UltraMemoryCommand
    data class Forget(val query: String) : UltraMemoryCommand
    data class Pin(val query: String) : UltraMemoryCommand
    data class Archive(val query: String) : UltraMemoryCommand
    data class Delete(val query: String) : UltraMemoryCommand
    data object ClearHistory : UltraMemoryCommand
    data object ClearAll : UltraMemoryCommand
    data object Enable : UltraMemoryCommand
    data object Disable : UltraMemoryCommand
}

object UltraMemoryCommandParser {
    fun parse(message: String): UltraMemoryCommand? {
        val cleanOriginal = message.trim()
        if (cleanOriginal.isBlank()) return null

        val withoutWakeWord = cleanOriginal
            .replace(Regex("""^\s*ultra\s*[,;:\-]?\s*""", RegexOption.IGNORE_CASE), "")
            .trim()

        val normalized = normalize(withoutWakeWord)
        if (normalized.isBlank()) return null

        if (
            normalized == "borra mi historial" ||
            normalized == "borra el historial" ||
            normalized == "elimina mi historial" ||
            normalized == "elimina el historial" ||
            normalized == "limpia mi historial" ||
            normalized == "clear my history" ||
            normalized == "clear history"
        ) {
            return UltraMemoryCommand.ClearHistory
        }

        if (
            normalized == "borra toda mi memoria" ||
            normalized == "borra toda la memoria" ||
            normalized == "olvida todo" ||
            normalized == "forget everything" ||
            normalized == "clear all memory"
        ) {
            return UltraMemoryCommand.ClearAll
        }

        if (
            normalized == "desactiva la memoria" ||
            normalized == "deshabilita la memoria" ||
            normalized == "no recuerdes nada" ||
            normalized == "disable memory" ||
            normalized == "turn memory off"
        ) {
            return UltraMemoryCommand.Disable
        }

        if (
            normalized == "activa la memoria" ||
            normalized == "habilita la memoria" ||
            normalized == "enable memory" ||
            normalized == "turn memory on"
        ) {
            return UltraMemoryCommand.Enable
        }

        extractPayload(
            original = withoutWakeWord,
            patterns = listOf(
                Regex("""^\s*recuerda\s+que\s+(.+?)\s*$""", RegexOption.IGNORE_CASE),
                Regex("""^\s*recuerda\s+(.+?)\s*$""", RegexOption.IGNORE_CASE),
                Regex("""^\s*remember\s+that\s+(.+?)\s*$""", RegexOption.IGNORE_CASE),
                Regex("""^\s*remember\s+(.+?)\s*$""", RegexOption.IGNORE_CASE)
            )
        )?.let { return UltraMemoryCommand.Remember(it) }

        extractPayload(
            original = withoutWakeWord,
            patterns = listOf(
                Regex("""^\s*olvida\s+que\s+(.+?)\s*$""", RegexOption.IGNORE_CASE),
                Regex("""^\s*olvida\s+(.+?)\s*$""", RegexOption.IGNORE_CASE),
                Regex("""^\s*forget\s+that\s+(.+?)\s*$""", RegexOption.IGNORE_CASE),
                Regex("""^\s*forget\s+(.+?)\s*$""", RegexOption.IGNORE_CASE)
            )
        )?.let { return UltraMemoryCommand.Forget(it) }

        extractPayload(
            original = withoutWakeWord,
            patterns = listOf(
                Regex("""^\s*fija\s+(.+?)\s*$""", RegexOption.IGNORE_CASE),
                Regex("""^\s*ancla\s+(.+?)\s*$""", RegexOption.IGNORE_CASE),
                Regex("""^\s*pin\s+(.+?)\s*$""", RegexOption.IGNORE_CASE)
            )
        )?.let { return UltraMemoryCommand.Pin(it) }

        extractPayload(
            original = withoutWakeWord,
            patterns = listOf(
                Regex("""^\s*archiva\s+(.+?)\s*$""", RegexOption.IGNORE_CASE),
                Regex("""^\s*archive\s+(.+?)\s*$""", RegexOption.IGNORE_CASE)
            )
        )?.let { return UltraMemoryCommand.Archive(it) }

        extractPayload(
            original = withoutWakeWord,
            patterns = listOf(
                Regex("""^\s*elimina\s+de\s+tu\s+memoria\s+(.+?)\s*$""", RegexOption.IGNORE_CASE),
                Regex("""^\s*borra\s+de\s+tu\s+memoria\s+(.+?)\s*$""", RegexOption.IGNORE_CASE),
                Regex("""^\s*delete\s+from\s+memory\s+(.+?)\s*$""", RegexOption.IGNORE_CASE)
            )
        )?.let { return UltraMemoryCommand.Delete(it) }

        return null
    }

    private fun extractPayload(original: String, patterns: List<Regex>): String? =
        patterns.firstNotNullOfOrNull { pattern ->
            pattern.matchEntire(original)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
                ?.trimEnd('.', '!', '?')
                ?.trim()
                ?.takeIf(String::isNotBlank)
        }

    private fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("""\p{M}+"""), "")
            .replace(Regex("""[^a-z0-9 ]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
}

object UltraMemorySafety {
    private val secretPatterns = listOf(
        Regex("""\b(password|passwd|passcode|contrasena|clave de acceso)\b"""),
        Regex("""\b(api[ _-]?key|api[ _-]?token|auth[ _-]?token|access[ _-]?token|refresh[ _-]?token)\b"""),
        Regex("""\b(bearer|client[ _-]?secret|private[ _-]?key|secret key|secreto)\b"""),
        Regex("""\b(credential|credentials|credencial|credenciales)\b""")
    )

    fun canPersist(text: String): Boolean {
        val clean = normalize(text)
        if (clean.isBlank() || clean.length > 4_000) return false
        return secretPatterns.none { it.containsMatchIn(clean) }
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("""\p{M}+"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
}

object UltraMemoryRetrieval {
    private val stopWords = setOf(
        "a", "al", "algo", "con", "de", "del", "el", "en", "es", "este", "esta",
        "la", "las", "lo", "los", "me", "mi", "para", "por", "que", "un", "una", "y",
        "about", "and", "for", "in", "is", "my", "of", "on", "the", "to", "what"
    )

    fun relevant(
        query: String,
        records: List<UltraStoredMemory>,
        scope: UltraMemoryScope,
        limit: Int = 6
    ): List<UltraMemoryRecall> {
        val safeLimit = limit.coerceIn(1, 50)
        val queryTokens = tokens(query)
        val broadRecall = isBroadRecallQuery(query)

        return records.asSequence()
            .filterNot { it.archived }
            .filter { it.scope.userId == scope.userId }
            .filter {
                it.scope.gamePackage == null ||
                    it.scope.gamePackage == scope.gamePackage
            }
            .mapNotNull { record ->
                val recordTokens = tokens(record.text)
                val overlap = if (queryTokens.isEmpty()) {
                    0
                } else {
                    queryTokens.intersect(recordTokens).size
                }
                val lexicalScore = if (queryTokens.isEmpty()) {
                    0.0
                } else {
                    overlap.toDouble() / max(queryTokens.size, 1)
                }
                val scopeBoost = if (
                    scope.gamePackage != null &&
                    record.scope.gamePackage == scope.gamePackage
                ) {
                    0.20
                } else {
                    0.05
                }
                val pinBoost = if (record.pinned) 0.35 else 0.0
                val factBoost = if (record.kind == UltraMemoryKind.FACT) 0.10 else 0.0
                val broadBoost = if (broadRecall) 0.20 else 0.0
                val score = lexicalScore + scopeBoost + pinBoost + factBoost + broadBoost

                if (overlap == 0 && !record.pinned && !broadRecall) {
                    null
                } else {
                    UltraMemoryRecall(
                        record = record,
                        score = score,
                        provenance = when (record.kind) {
                            UltraMemoryKind.FACT -> UltraMemoryProvenance.REMEMBERED_FACT
                            UltraMemoryKind.CONVERSATION -> UltraMemoryProvenance.PRIOR_CONVERSATION
                            UltraMemoryKind.SUMMARY -> UltraMemoryProvenance.SUMMARY
                        }
                    )
                }
            }
            .sortedWith(
                compareByDescending<UltraMemoryRecall> { it.score }
                    .thenByDescending { it.record.pinned }
                    .thenByDescending { it.record.timestampMillis }
            )
            .take(safeLimit)
            .toList()
    }

    private fun isBroadRecallQuery(query: String): Boolean {
        val normalized = normalize(query)
        return listOf(
            "que recuerdas",
            "que sabes de mi",
            "recuerdas de mi",
            "what do you remember",
            "what do you know about me"
        ).any(normalized::contains)
    }

    private fun tokens(value: String): Set<String> =
        normalize(value)
            .split(' ')
            .asSequence()
            .filter { it.length >= 2 }
            .filterNot(stopWords::contains)
            .toSet()

    private fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("""\p{M}+"""), "")
            .replace(Regex("""[^a-z0-9 ]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
}

object UltraConversationDelta {
    fun newEntries(previous: List<String>, next: List<String>): List<String> {
        if (next.isEmpty()) return emptyList()
        if (previous.isEmpty()) return next

        val maxOverlap = minOf(previous.size, next.size)
        for (overlap in maxOverlap downTo 1) {
            if (previous.takeLast(overlap) == next.take(overlap)) {
                return next.drop(overlap)
            }
        }

        return if (previous == next) emptyList() else next
    }
}

object UltraMemoryCodec {
    private const val VERSION = 1
    private const val HEADER = "ULTRA_MEMORY_V1"

    fun encode(snapshot: UltraMemorySnapshot): String = buildString {
        append(HEADER)
        append('|')
        append(if (snapshot.enabled) "1" else "0")
        snapshot.records.forEach { record ->
            append('\n')
            append(
                listOf(
                    "R",
                    encodeField(record.id),
                    record.kind.name,
                    record.role.name,
                    encodeField(record.text),
                    record.timestampMillis.toString(),
                    encodeField(record.scope.userId),
                    encodeField(record.scope.gamePackage.orEmpty()),
                    encodeField(record.threadId),
                    if (record.pinned) "1" else "0",
                    if (record.archived) "1" else "0"
                ).joinToString("|")
            )
        }
    }

    fun decode(raw: String): UltraMemorySnapshot {
        if (raw.isBlank()) return UltraMemorySnapshot()

        val lines = raw.lineSequence().filter(String::isNotBlank).toList()
        if (lines.isEmpty()) return UltraMemorySnapshot()

        val header = lines.first().split('|')
        if (header.size != 2 || header[0] != HEADER) {
            return UltraMemorySnapshot()
        }

        val enabled = header[1] != "0"
        val records = lines.drop(1).mapNotNull(::decodeRecord)
        return UltraMemorySnapshot(enabled = enabled, records = records)
    }

    @Suppress("UNUSED_PARAMETER")
    fun currentVersion(): Int = VERSION

    private fun decodeRecord(line: String): UltraStoredMemory? = runCatching {
        val fields = line.split('|')
        if (fields.size != 11 || fields[0] != "R") return null

        val gamePackage = decodeField(fields[7]).takeIf(String::isNotBlank)
        UltraStoredMemory(
            id = decodeField(fields[1]).takeIf(String::isNotBlank) ?: return null,
            kind = UltraMemoryKind.valueOf(fields[2]),
            role = UltraMemoryRole.valueOf(fields[3]),
            text = decodeField(fields[4]).takeIf(String::isNotBlank) ?: return null,
            timestampMillis = fields[5].toLongOrNull() ?: return null,
            scope = UltraMemoryScope(
                userId = decodeField(fields[6]).takeIf(String::isNotBlank) ?: "local",
                gamePackage = gamePackage
            ),
            threadId = decodeField(fields[8])
                .takeIf(String::isNotBlank)
                ?: UltraStoredMemory.DEFAULT_THREAD_ID,
            pinned = fields[9] == "1",
            archived = fields[10] == "1"
        )
    }.getOrNull()

    private fun encodeField(value: String): String {
        if (value.isEmpty()) return "_"
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(value.toByteArray(StandardCharsets.UTF_8))
    }

    private fun decodeField(value: String): String {
        if (value == "_") return ""
        return runCatching {
            String(
                Base64.getUrlDecoder().decode(value),
                StandardCharsets.UTF_8
            )
        }.getOrDefault("")
    }
}

interface UltraLongTermMemoryGateway {
    fun handleCommand(message: String, scope: UltraMemoryScope): String?
    fun recallContext(
        message: String,
        scope: UltraMemoryScope,
        limit: Int = 6
    ): List<UltraMemoryRecall>
}
