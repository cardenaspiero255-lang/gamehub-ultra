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
    fun commonSpokenGameAbbreviationsMatchInstalledTitles() {
        val installed = listOf(
            GameInfo("com.example.re4", "Resident Evil 4 Remake"),
            GameInfo("com.example.re2", "Resident Evil 2"),
            GameInfo("com.example.gtav", "Grand Theft Auto V"),
            GameInfo("com.example.bo3", "Call of Duty: Black Ops III")
        )

        assertEquals("com.example.re4", GameMatchFinder.find("RE4", installed)?.packageName)
        assertEquals("com.example.re2", GameMatchFinder.find("RE2", installed)?.packageName)
        assertEquals("com.example.gtav", GameMatchFinder.find("GTA V", installed)?.packageName)
        assertEquals("com.example.bo3", GameMatchFinder.find("BO3", installed)?.packageName)
    }

    @Test
    fun spokenAcronymMatchesPunctuatedGameLabel() {
        val games = listOf(GameInfo("com.zeptolab.cats.google", "C.A.T.S."))
        val match = GameMatchFinder.find("cats", games)
        assertEquals("com.zeptolab.cats.google", match?.packageName)
    }


    @Test
    fun popularAliasesMatchTitlesThatCannotBeDerivedFromInitials() {
        val installed = listOf(
            GameInfo("com.example.re8", "Resident Evil Village"),
            GameInfo("com.example.cp2077", "Cyberpunk 2077"),
            GameInfo("com.example.pubg", "PLAYERUNKNOWN'S BATTLEGROUNDS")
        )

        assertEquals("com.example.re8", GameMatchFinder.find("RE8", installed)?.packageName)
        assertEquals("com.example.cp2077", GameMatchFinder.find("CP2077", installed)?.packageName)
        assertEquals("com.example.pubg", GameMatchFinder.find("PUBG", installed)?.packageName)
    }

    @Test
    fun ambiguousPopularAliasDoesNotLaunchArbitraryGame() {
        val installed = listOf(
            GameInfo("com.example.re8", "Resident Evil Village"),
            GameInfo("com.example.re8demo", "Resident Evil Village Demo")
        )

        assertEquals(null, GameMatchFinder.find("RE8", installed))
    }


    @Test
    fun savesAndUsesUserDefinedGameAlias() {
        val installed = listOf(
            GameInfo("com.supercell.brawlstars", "Brawl Stars"),
            GameInfo("com.example.bs", "Battle Simulator")
        )
        val aliases = linkedMapOf<String, String>()

        val saved = VoiceCommandEngine.execute(
            command = VoiceCommand.DefineGameAlias("bs", "brawl stars"),
            gamesProvider = { installed },
            launchGame = { true },
            saveSelectedGame = {},
            saveSelectedProfile = {},
            isProfileAvailable = { true },
            statusProvider = { VoiceDeviceStatus(80, "Normal") },
            gameAliasesProvider = { aliases },
            saveGameAlias = { alias, packageName -> aliases[alias] = packageName }
        )
        val aliasSaved = assertIs<VoiceActionResult.GameAliasSaved>(saved)
        assertEquals("bs", aliasSaved.alias)
        assertEquals("com.supercell.brawlstars", aliases["bs"])

        var launched = ""
        val opened = VoiceCommandEngine.execute(
            command = VoiceCommand.OpenGame("BS"),
            gamesProvider = { installed },
            launchGame = { packageName -> launched = packageName; true },
            saveSelectedGame = {},
            saveSelectedProfile = {},
            isProfileAvailable = { true },
            statusProvider = { VoiceDeviceStatus(80, "Normal") },
            gameAliasesProvider = { aliases },
            saveGameAlias = { _, _ -> }
        )
        assertIs<VoiceActionResult.GameOpened>(opened)
        assertEquals("com.supercell.brawlstars", launched)
    }

    @Test
    fun refusesToSaveAliasWhenTargetGameIsAmbiguous() {
        val installed = listOf(
            GameInfo("a", "Resident Evil"),
            GameInfo("b", "Resident Evil Village")
        )
        var writes = 0
        val result = VoiceCommandEngine.execute(
            command = VoiceCommand.DefineGameAlias("re", "resident evil"),
            gamesProvider = { installed },
            launchGame = { true },
            saveSelectedGame = {},
            saveSelectedProfile = {},
            isProfileAvailable = { true },
            statusProvider = { VoiceDeviceStatus(80, "Normal") },
            saveGameAlias = { _, _ -> writes++ }
        )
        assertIs<VoiceActionResult.NotAvailable>(result)
        assertEquals(0, writes)
    }


    @Test
    fun spacedInitialsResolveExistingLearnedAlias() {
        val installed = listOf(GameInfo("com.supercell.brawlstars", "Brawl Stars"))
        val match = GameMatchFinder.find(
            query = "B S",
            games = installed,
            userAliases = mapOf("bs" to "com.supercell.brawlstars")
        )
        assertEquals("com.supercell.brawlstars", match?.packageName)
    }

    @Test
    fun resolverOwnedAliasCannotBeSaved() {
        val installed = listOf(GameInfo("com.supercell.brawlstars", "Brawl Stars"))
        var writes = 0
        val resolver = object : NaturalLanguageIntentResolver {
            override fun resolve(transcript: String): VoiceCommand? =
                if (transcript == "optimize my game") VoiceCommand.AskAi(transcript) else null
        }

        val result = VoiceCommandEngine.execute(
            command = VoiceCommand.DefineGameAlias("optimize my game", "Brawl Stars"),
            gamesProvider = { installed },
            aliasGamesProvider = { installed },
            launchGame = { true },
            saveSelectedGame = {},
            saveSelectedProfile = {},
            isProfileAvailable = { true },
            statusProvider = { VoiceDeviceStatus(80, "Normal") },
            aliasIntentResolver = resolver,
            saveGameAlias = { _, _ -> writes++ }
        )

        assertIs<VoiceActionResult.NotAvailable>(result)
        assertEquals(0, writes)
    }


    @Test
    fun memoryCommandAliasCannotBeSaved() {
        val installed = listOf(GameInfo("com.supercell.brawlstars", "Brawl Stars"))
        var writes = 0

        val result = VoiceCommandEngine.execute(
            command = VoiceCommand.DefineGameAlias("remember that pizza", "Brawl Stars"),
            gamesProvider = { installed },
            aliasGamesProvider = { installed },
            launchGame = { true },
            saveSelectedGame = {},
            saveSelectedProfile = {},
            isProfileAvailable = { true },
            statusProvider = { VoiceDeviceStatus(80, "Normal") },
            saveGameAlias = { _, _ -> writes++ }
        )

        assertIs<VoiceActionResult.NotAvailable>(result)
        assertEquals(0, writes)
    }

    @Test
    fun reservedProfileAliasCannotBeSavedAsGameAlias() {
        val installed = listOf(GameInfo("com.example.x4", "X4 Foundations"))
        var writes = 0
        val result = VoiceCommandEngine.execute(
            command = VoiceCommand.DefineGameAlias("x4", "X4 Foundations"),
            gamesProvider = { installed },
            launchGame = { true },
            saveSelectedGame = {},
            saveSelectedProfile = {},
            isProfileAvailable = { true },
            statusProvider = { VoiceDeviceStatus(80, "Normal") },
            saveGameAlias = { _, _ -> writes++ }
        )
        assertIs<VoiceActionResult.NotAvailable>(result)
        assertEquals(0, writes)
    }


    @Test
    fun learnedAliasCannotTargetLaunchableNonGameApp() {
        val launchable = listOf(
            GameInfo("com.supercell.brawlstars", "Brawl Stars"),
            GameInfo("com.example.settings", "Device Settings")
        )
        val gamesOnly = listOf(
            GameInfo("com.supercell.brawlstars", "Brawl Stars")
        )
        var writes = 0
        val result = VoiceCommandEngine.execute(
            command = VoiceCommand.DefineGameAlias("cfg", "Device Settings"),
            gamesProvider = { launchable },
            aliasGamesProvider = { gamesOnly },
            launchGame = { true },
            saveSelectedGame = {},
            saveSelectedProfile = {},
            isProfileAvailable = { true },
            statusProvider = { VoiceDeviceStatus(80, "Normal") },
            saveGameAlias = { _, _ -> writes++ }
        )

        assertIs<VoiceActionResult.NotAvailable>(result)
        assertEquals(0, writes)
    }

    @Test
    fun staleAliasToNonGameAppIsIgnored() {
        val launchable = listOf(
            GameInfo("com.supercell.brawlstars", "Brawl Stars"),
            GameInfo("com.example.settings", "Device Settings")
        )
        val gamesOnly = listOf(
            GameInfo("com.supercell.brawlstars", "Brawl Stars")
        )
        var launched = ""
        val result = VoiceCommandEngine.execute(
            command = VoiceCommand.OpenGame("CFG"),
            gamesProvider = { launchable },
            aliasGamesProvider = { gamesOnly },
            launchGame = { packageName -> launched = packageName; true },
            saveSelectedGame = {},
            saveSelectedProfile = {},
            isProfileAvailable = { true },
            statusProvider = { VoiceDeviceStatus(80, "Normal") },
            gameAliasesProvider = { mapOf("cfg" to "com.example.settings") }
        )

        assertIs<VoiceActionResult.NotAvailable>(result)
        assertEquals("", launched)
    }


    @Test
    fun unsafePersistedAliasDoesNotFallBackToMatchingLaunchableApp() {
        val launchable = listOf(
            GameInfo("com.discord", "Discord"),
            GameInfo("com.supercell.brawlstars", "Brawl Stars")
        )
        val gamesOnly = listOf(
            GameInfo("com.supercell.brawlstars", "Brawl Stars")
        )
        var launched = ""

        val result = VoiceCommandEngine.execute(
            command = VoiceCommand.OpenGame("discord"),
            gamesProvider = { launchable },
            aliasGamesProvider = { gamesOnly },
            launchGame = { packageName -> launched = packageName; true },
            saveSelectedGame = {},
            saveSelectedProfile = {},
            isProfileAvailable = { true },
            statusProvider = { VoiceDeviceStatus(80, "Normal") },
            gameAliasesProvider = {
                mapOf("discord" to "com.example.uninstalled.game")
            }
        )

        assertIs<VoiceActionResult.NotAvailable>(result)
        assertEquals("", launched)
    }

    @Test
    fun openGameSnapshotsAliasCatalogOnlyOnce() {
        var aliasCatalogReads = 0

        val result = VoiceCommandEngine.execute(
            command = VoiceCommand.OpenGame("Minecraft"),
            gamesProvider = { games },
            aliasGamesProvider = {
                aliasCatalogReads++
                games
            },
            launchGame = { true },
            saveSelectedGame = {},
            saveSelectedProfile = {},
            isProfileAvailable = { true },
            statusProvider = { VoiceDeviceStatus(80, "Normal") }
        )

        assertIs<VoiceActionResult.GameOpened>(result)
        assertEquals(1, aliasCatalogReads)
    }

    @Test
    fun aliasesPreemptedByHigherPriorityCommandsCannotBeSaved() {
        val installed = listOf(GameInfo("com.supercell.brawlstars", "Brawl Stars"))
        for (alias in listOf("balanced fps", "battery")) {
            var writes = 0
            val result = VoiceCommandEngine.execute(
                command = VoiceCommand.DefineGameAlias(alias, "Brawl Stars"),
                gamesProvider = { installed },
                aliasGamesProvider = { installed },
                launchGame = { true },
                saveSelectedGame = {},
                saveSelectedProfile = {},
                isProfileAvailable = { true },
                statusProvider = { VoiceDeviceStatus(80, "Normal") },
                saveGameAlias = { _, _ -> writes++ }
            )
            assertIs<VoiceActionResult.NotAvailable>(result)
            assertEquals(0, writes)
        }
    }

    @Test
    fun builtInPopularAliasCannotLaunchNonGameApp() {
        val launchable = listOf(GameInfo("com.example.pubgguide", "PUBG Guide"))
        var launched = ""
        val result = VoiceCommandEngine.execute(
            command = VoiceCommand.OpenGame("PUBG"),
            gamesProvider = { launchable },
            aliasGamesProvider = { emptyList() },
            launchGame = { packageName -> launched = packageName; true },
            saveSelectedGame = {},
            saveSelectedProfile = {},
            isProfileAvailable = { true },
            statusProvider = { VoiceDeviceStatus(80, "Normal") }
        )

        assertIs<VoiceActionResult.NotAvailable>(result)
        assertEquals("", launched)
    }

    @Test
    fun exactInstalledTitleWinsOverBuiltInAlias() {
        val installed = listOf(
            GameInfo("com.example.re8", "RE8"),
            GameInfo("com.capcom.village", "Resident Evil Village")
        )

        assertEquals(
            "com.example.re8",
            GameMatchFinder.find("RE8", installed)?.packageName
        )
    }


    @Test
    fun ordinaryMultiwordAliasCanBeSaved() {
        val installed = listOf(GameInfo("com.supercell.brawlstars", "Brawl Stars"))
        var savedAlias = ""
        val result = VoiceCommandEngine.execute(
            command = VoiceCommand.DefineGameAlias("the crew", "Brawl Stars"),
            gamesProvider = { installed },
            aliasGamesProvider = { installed },
            launchGame = { true },
            saveSelectedGame = {},
            saveSelectedProfile = {},
            isProfileAvailable = { true },
            statusProvider = { VoiceDeviceStatus(80, "Normal") },
            saveGameAlias = { alias, _ -> savedAlias = alias }
        )

        assertIs<VoiceActionResult.GameAliasSaved>(result)
        assertEquals("the crew", savedAlias)
    }

}
