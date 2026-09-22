package com.cardenaspiero255.gamehubultra.voice

import com.cardenaspiero255.gamehubultra.GameInfo
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile

data class VoiceDeviceStatus(
    val batteryPercent: Int?,
    val thermalLabel: String
)

sealed interface VoiceActionResult {
    data class ProfileApplied(val profile: PerformanceProfile) : VoiceActionResult
    data class GameOpened(
        val game: GameInfo,
        val profile: PerformanceProfile?,
        val profileUnavailable: Boolean
    ) : VoiceActionResult
    data class DeviceStatus(val status: VoiceDeviceStatus) : VoiceActionResult
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
        isProfileAvailable: (PerformanceProfile) -> Boolean,
        statusProvider: () -> VoiceDeviceStatus
    ): VoiceActionResult =
        when (command) {
            is VoiceCommand.SelectProfile -> {
                if (!isProfileAvailable(command.profile)) {
                    VoiceActionResult.NotAvailable(
                        "El perfil " + command.profile.title + " no está disponible en este dispositivo."
                    )
                } else {
                    saveSelectedProfile(command.profile)
                    VoiceActionResult.ProfileApplied(command.profile)
                }
            }

            is VoiceCommand.OpenGame -> {
                val match = GameMatchFinder.find(command.query, gamesProvider())
                if (match == null) {
                    VoiceActionResult.NotAvailable(
                        "No encontré un juego instalado que coincida con "" + command.query + ""."
                    )
                } else {
                    saveSelectedGame(match.packageName)
                    val profileUnavailable =
                        command.requestedProfile != null &&
                            !isProfileAvailable(command.requestedProfile)

                    if (!profileUnavailable && command.requestedProfile != null) {
                        saveSelectedProfile(command.requestedProfile)
                    }

                    if (launchGame(match.packageName)) {
                        VoiceActionResult.GameOpened(
                            game = match,
                            profile = command.requestedProfile?.takeUnless { profileUnavailable },
                            profileUnavailable = profileUnavailable
                        )
                    } else {
                        VoiceActionResult.Failed(
                            "Encontré " + match.label + ", pero Android no permitió abrirlo."
                        )
                    }
                }
            }

            VoiceCommand.DeviceStatus -> VoiceActionResult.DeviceStatus(statusProvider())
            VoiceCommand.Help -> VoiceActionResult.Help
            is VoiceCommand.Unknown ->
                VoiceActionResult.NotAvailable(
                    "No reconocí el comando. Prueba: abre un juego, pon X4 o dime la temperatura."
                )
        }
}

internal object GameMatchFinder {
    fun find(query: String, games: List<GameInfo>): GameInfo? {
        if (games.isEmpty()) return null

        val normalizedQuery = VoiceCommandParser.normalize(query)
        games.firstOrNull {
            VoiceCommandParser.normalize(it.label) == normalizedQuery ||
                it.packageName.equals(query.trim(), ignoreCase = true)
        }?.let { return it }

        val scored = games.map { game ->
            val label = VoiceCommandParser.normalize(game.label)
            val queryTokens = normalizedQuery.split(' ').filter(String::isNotBlank).toSet()
            val labelTokens = label.split(' ').filter(String::isNotBlank).toSet()
            val overlap = if (queryTokens.isEmpty()) 0.0
            else queryTokens.intersect(labelTokens).size.toDouble() / queryTokens.size

            game to when {
                label.contains(normalizedQuery) -> 1.0
                normalizedQuery.contains(label) -> 0.9
                else -> overlap
            }
        }.sortedByDescending { it.second }

        val best = scored.firstOrNull() ?: return null
        val second = scored.getOrNull(1)

        return if (
            best.second >= 0.6 &&
            (second == null || best.second - second.second >= 0.15 || best.second == 1.0)
        ) best.first else null
    }
}
