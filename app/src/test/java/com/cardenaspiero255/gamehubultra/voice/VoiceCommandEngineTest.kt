package com.cardenaspiero255.gamehubultra.voice

import com.cardenaspiero255.gamehubultra.GameInfo
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class VoiceCommandEngineTest {
    private val games = listOf(
        GameInfo("re4", "Resident Evil 4 Remake"),
        GameInfo("minecraft", "Minecraft")
    )

    @Test
    fun opensExactGame() {
        var launched = ""
        val result = VoiceCommandEngine.execute(
            command = VoiceCommand.OpenGame("Resident Evil 4 Remake"),
            gamesProvider = { games },
            launchGame = { launched = it; true },
            saveSelectedGame = {},
            saveSelectedProfile = {},
            isProfileAvailable = { true },
            statusProvider = { VoiceDeviceStatus(80, "Normal") }
        )
        assertIs<VoiceActionResult.GameOpened>(result)
        assertEquals("re4", launched)
    }

    @Test
    fun refusesUnsupportedX4WithoutSelectingIt() {
        var saved: PerformanceProfile? = null
        val result = VoiceCommandEngine.execute(
            command = VoiceCommand.SelectProfile(PerformanceProfile.X4),
            gamesProvider = { games },
            launchGame = { true },
            saveSelectedGame = {},
            saveSelectedProfile = { saved = it },
            isProfileAvailable = { false },
            statusProvider = { VoiceDeviceStatus(80, "Normal") }
        )
        assertIs<VoiceActionResult.NotAvailable>(result)
        assertEquals(null, saved)
    }

    @Test
    fun opensGameAndReportsUnsupportedRequestedProfile() {
        val result = VoiceCommandEngine.execute(
            command = VoiceCommand.OpenGame("Resident Evil 4 Remake", PerformanceProfile.X4),
            gamesProvider = { games },
            launchGame = { true },
            saveSelectedGame = {},
            saveSelectedProfile = {},
            isProfileAvailable = { false },
            statusProvider = { VoiceDeviceStatus(80, "Normal") }
        )
        val opened = assertIs<VoiceActionResult.GameOpened>(result)
        assertTrue(opened.profileUnavailable)
        assertEquals(null, opened.profile)
    }

    @Test
    fun rejectsAmbiguousGameMatches() {
        val ambiguous = listOf(
            GameInfo("a", "Resident Evil"),
            GameInfo("b", "Resident Evil Village")
        )
        val result = VoiceCommandEngine.execute(
            command = VoiceCommand.OpenGame("Resident Evil"),
            gamesProvider = { ambiguous },
            launchGame = { true },
            saveSelectedGame = {},
            saveSelectedProfile = {},
            isProfileAvailable = { true },
            statusProvider = { VoiceDeviceStatus(80, "Normal") }
        )
        assertIs<VoiceActionResult.NotAvailable>(result)
    }
}
