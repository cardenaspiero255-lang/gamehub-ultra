package com.cardenaspiero255.gamehubultra.voice

import com.cardenaspiero255.gamehubultra.GameInfo
import com.cardenaspiero255.gamehubultra.ai.GameHubAiAdvice
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile

data class VoiceDeviceStatus(
    val batteryPercent: Int?,
    val thermalLabel: String
)

sealed interface VoiceActionResult {
    data class ProfileSelected(
        val profile: PerformanceProfile,
        val deferred: Boolean
    ) : VoiceActionResult

    data class GameOpened(
        val game: GameInfo,
        val profile: PerformanceProfile?,
        val profileUnavailable: Boolean,
        val profileDeferred: Boolean
    ) : VoiceActionResult

    data class GameAliasSaved(
        val alias: String,
        val game: GameInfo
    ) : VoiceActionResult

    data class DeviceStatus(val status: VoiceDeviceStatus) : VoiceActionResult
    data class AiAdvice(val advice: GameHubAiAdvice) : VoiceActionResult
    data object Help : VoiceActionResult
    data class NotAvailable(val detail: String) : VoiceActionResult
    data object RequiresPermission : VoiceActionResult
    data class Failed(val detail: String) : VoiceActionResult
}

object VoiceCommandEngine {
    fun execute(
        command: VoiceCommand,
        gamesProvider: () -> List<GameInfo>,
        launchGame: (String) -> Boolean,
        saveSelectedGame: (String) -> Unit,
        saveSelectedProfile: (PerformanceProfile) -> Unit,
        saveSelectedGameWithProfile: ((String, PerformanceProfile) -> Unit)? = null,
        isProfileAvailable: (PerformanceProfile) -> Boolean,
        statusProvider: () -> VoiceDeviceStatus,
        deferProfileApplication: Boolean = false,
        aiAdvisor: ((String) -> GameHubAiAdvice)? = null,
        gameAliasesProvider: () -> Map<String, String> = { emptyMap() },
        saveGameAlias: (String, String) -> Unit = { _, _ -> }
    ): VoiceActionResult =
        when (command) {
            is VoiceCommand.SelectProfile -> {
                if (!isProfileAvailable(command.profile)) {
                    VoiceActionResult.NotAvailable(
                        "El perfil " + command.profile.title + " no está disponible en este dispositivo."
                    )
                } else {
                    saveSelectedProfile(command.profile)
                    VoiceActionResult.ProfileSelected(
                        profile = command.profile,
                        deferred = deferProfileApplication
                    )
                }
            }

            is VoiceCommand.DefineGameAlias -> {
                val normalizedAlias = VoiceCommandParser.normalize(command.alias)
                val target = GameMatchFinder.find(
                    query = command.gameQuery,
                    games = gamesProvider(),
                    userAliases = emptyMap()
                )
                if (target == null || normalizedAlias.length !in 2..20) {
                    VoiceActionResult.NotAvailable(
                        "No pude asociar el alias ${command.alias} a un único juego instalado."
                    )
                } else {
                    saveGameAlias(normalizedAlias, target.packageName)
                    VoiceActionResult.GameAliasSaved(
                        alias = normalizedAlias,
                        game = target
                    )
                }
            }

            is VoiceCommand.OpenGame -> {
                val match = GameMatchFinder.find(
                    query = command.query,
                    games = gamesProvider(),
                    userAliases = gameAliasesProvider()
                )
                if (match == null) {
                    VoiceActionResult.NotAvailable(
                        "No encontré un juego instalado que coincida con \"" + command.query + "\"."
                    )
                } else {
                    val profileUnavailable =
                        command.requestedProfile != null &&
                            !isProfileAvailable(command.requestedProfile)

                    when {
                        command.requestedProfile == null ->
                            saveSelectedGame(match.packageName)
                        profileUnavailable ->
                            saveSelectedGame(match.packageName)
                        saveSelectedGameWithProfile != null ->
                            saveSelectedGameWithProfile(match.packageName, command.requestedProfile)
                        else -> {
                            saveSelectedGame(match.packageName)
                            saveSelectedProfile(command.requestedProfile)
                        }
                    }

                    if (launchGame(match.packageName)) {
                        VoiceActionResult.GameOpened(
                            game = match,
                            profile = command.requestedProfile?.takeUnless { profileUnavailable },
                            profileUnavailable = profileUnavailable,
                            profileDeferred =
                                command.requestedProfile != null &&
                                    !profileUnavailable &&
                                    deferProfileApplication
                        )
                    } else {
                        VoiceActionResult.Failed(
                            "Encontré " + match.label + ", pero Android no permitió abrirlo."
                        )
                    }
                }
            }

            VoiceCommand.DeviceStatus -> VoiceActionResult.DeviceStatus(statusProvider())
            is VoiceCommand.AskAi ->
                aiAdvisor?.invoke(command.question)?.let(VoiceActionResult::AiAdvice)
                    ?: VoiceActionResult.NotAvailable("El asesor IA no está disponible sin un proveedor local compatible.")
            VoiceCommand.Help -> VoiceActionResult.Help
            is VoiceCommand.Unknown ->
                VoiceActionResult.NotAvailable(
                    "No reconocí el comando. Prueba: abre un juego, pon X4 o dime la temperatura."
                )
        }
}

internal object GameMatchFinder {
    private val popularAliases = mapOf(
        "re8" to listOf("resident evil village"),
        "cp2077" to listOf("cyberpunk 2077"),
        "pubg" to listOf("playerunknown s battlegrounds", "pubg")
    )

    fun find(
        query: String,
        games: List<GameInfo>,
        userAliases: Map<String, String> = emptyMap()
    ): GameInfo? {
        if (games.isEmpty()) return null

        val normalizedQuery = VoiceCommandParser.normalize(query)
        if (normalizedQuery.isBlank()) return null

        userAliases[normalizedQuery]?.let { packageName ->
            games.firstOrNull { it.packageName == packageName }?.let { return it }
        }

        popularAliases[normalizedQuery]?.let { targets ->
            val candidates = games.filter { game ->
                val label = VoiceCommandParser.normalize(game.label)
                targets.any { target ->
                    label == target || label.startsWith("${target} ")
                }
            }
            if (candidates.size == 1) return candidates.single()
            if (candidates.size > 1) return null
        }

        val queryTokens = normalizedQuery.split(" ").filter(String::isNotBlank)
        val compactQuery = normalizedQuery.replace(" ", "")
        val scored = games.map { game ->
            val label = VoiceCommandParser.normalize(game.label)
            val labelTokens = label.split(" ").filter(String::isNotBlank)
            val compactLabel = label.replace(" ", "")
            val tokenScore = if (queryTokens.isEmpty() || labelTokens.isEmpty()) 0.0
            else queryTokens.map { q -> labelTokens.maxOfOrNull { l -> tokenSimilarity(q, l) } ?: 0.0 }.average()
            val abbreviationMatch = compactQuery.length >= 3 &&
                compactQuery in abbreviationCandidates(labelTokens)

            game to when {
                label == normalizedQuery || game.packageName.equals(query.trim(), ignoreCase = true) -> 1.0
                compactQuery.length >= 3 && compactLabel == compactQuery -> 0.98
                abbreviationMatch -> 0.97
                label.startsWith(normalizedQuery + " ") -> 0.94
                label.contains(normalizedQuery) -> 0.90
                normalizedQuery.contains(label) -> 0.86
                else -> tokenScore
            }
        }.sortedByDescending { it.second }

        val best = scored.firstOrNull() ?: return null
        val second = scored.getOrNull(1)
        val exactLabel = best.second == 1.0 &&
            VoiceCommandParser.normalize(best.first.label) == normalizedQuery
        val exactPackage = best.first.packageName.equals(query.trim(), ignoreCase = true)
        val ambiguousHumanMatch = exactLabel &&
            !exactPackage &&
            second != null &&
            second.second >= 0.86

        return if (
            best.second >= 0.55 &&
            !ambiguousHumanMatch &&
            (second == null || best.second - second.second >= 0.08 || exactPackage)
        ) best.first else null
    }

    private fun abbreviationCandidates(tokens: List<String>): Set<String> {
        if (tokens.size < 2) return emptySet()

        fun romanToArabic(token: String): String? = when (token) {
            "i" -> "1"
            "ii" -> "2"
            "iii" -> "3"
            "iv" -> "4"
            "v" -> "5"
            "vi" -> "6"
            "vii" -> "7"
            "viii" -> "8"
            "ix" -> "9"
            "x" -> "10"
            else -> null
        }

        fun rawPiece(token: String): String =
            if (token.all(Char::isDigit)) token else token.first().toString()

        fun numericPiece(token: String): String =
            romanToArabic(token) ?: rawPiece(token)

        fun addWindows(pieces: List<String>, destination: MutableSet<String>) {
            for (start in pieces.indices) {
                val builder = StringBuilder()
                for (end in start until pieces.size) {
                    builder.append(pieces[end])
                    if (end > start && builder.length >= 3) {
                        destination += builder.toString()
                    }
                }
            }
        }

        return linkedSetOf<String>().also { candidates ->
            addWindows(tokens.map(::rawPiece), candidates)
            addWindows(tokens.map(::numericPiece), candidates)
        }
    }

    private fun tokenSimilarity(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.length >= 4 && b.length >= 4 && (a.startsWith(b) || b.startsWith(a))) return 0.9
        val distance = levenshtein(a, b)
        val longest = maxOf(a.length, b.length)
        return if (longest == 0) 1.0 else 1.0 - distance.toDouble() / longest
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in a.indices) {
            current[0] = i + 1
            for (j in b.indices) {
                val cost = if (a[i] == b[j]) 0 else 1
                current[j + 1] = minOf(current[j] + 1, previous[j + 1] + 1, previous[j] + cost)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }
}
