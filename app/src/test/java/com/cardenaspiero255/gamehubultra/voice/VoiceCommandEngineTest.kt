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
    fun selectsSupportedProfileAndCanDeferApplication() {
        var saved: PerformanceProfile? = null
        val result = VoiceCommandEngine.execute(
            command = VoiceCommand.SelectProfile(PerformanceProfile.X4),
            gamesProvider = { games },
            launchGame = { true },
            saveSelectedGame = {},
            saveSelectedProfile = { saved = it },
            isProfileAvailable = { true },
            statusProvider = { VoiceDeviceStatus(80, "Normal") },
            deferProfileApplication = true
        )
        val selected = assertIs<VoiceActionResult.ProfileSelected>(result)
        assertTrue(selected.deferred)
        assertEquals(PerformanceProfile.X4, saved)
    }

    @Test
    fun appliesImmediatelyWhenUsedByInAppPath() {
        val result = VoiceCommandEngine.execute(
            command = VoiceCommand.SelectProfile(PerformanceProfile.BALANCED),
            gamesProvider = { games },
            launchGame = { true },
            saveSelectedGame = {},
            saveSelectedProfile = {},
            isProfileAvailable = { true },
            statusProvider = { VoiceDeviceStatus(80, "Normal") }
        )
        val selected = assertIs<VoiceActionResult.ProfileSelected>(result)
        assertEquals(false, selected.deferred)
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
        assertEquals(false, opened.profileDeferred)
    }

    @Test
    fun defersProfileWhenOpeningGameFromSystemAssistant() {
        val result = VoiceCommandEngine.execute(
            command = VoiceCommand.OpenGame("Minecraft", PerformanceProfile.X4),
            gamesProvider = { games },
            launchGame = { true },
            saveSelectedGame = {},
            saveSelectedProfile = {},
            isProfileAvailable = { true },
            statusProvider = { VoiceDeviceStatus(80, "Normal") },
            deferProfileApplication = true
        )
        val opened = assertIs<VoiceActionResult.GameOpened>(result)
        assertTrue(opened.profileDeferred)
        assertEquals(PerformanceProfile.X4, opened.profile)
    }

    @Test
    fun reportsFailedGameLaunch() {
        val result = VoiceCommandEngine.execute(
            command = VoiceCommand.OpenGame("Minecraft"),
            gamesProvider = { games },
            launchGame = { false },
            saveSelectedGame = {},
            saveSelectedProfile = {},
            isProfileAvailable = { true },
            statusProvider = { VoiceDeviceStatus(80, "Normal") }
        )
        val failed = assertIs<VoiceActionResult.Failed>(result)
        assertTrue(failed.detail.contains("Minecraft"))
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

    @Test
    fun acronymGameDoesNotMatchUnrelatedWord() {
        val cats = listOf(GameInfo("com.zeptolab.cats.google", "C.A.T.S."))
        assertEquals(null, GameMatchFinder.find("activa", cats))
    }

    @Test
    fun minorSpeechTypoStillMatchesInstalledGame() {
        val games = listOf(GameInfo("com.supercell.brawlstars", "Brawl Stars"))
        val match = GameMatchFinder.find("Bral Stars", games)
        assertEquals("com.supercell.brawlstars", match?.packageName)
    }


    @Test
    fun spokenAcronymMatchesPunctuatedGameLabel() {
        val games = listOf(GameInfo("com.zeptolab.cats.google", "C.A.T.S."))
        val match = GameMatchFinder.find("cats", games)
        assertEquals("com.zeptolab.cats.google", match?.packageName)
    }

}
