package com.cardenaspiero255.gamehubultra.data

import com.cardenaspiero255.gamehubultra.domain.GamePlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SteamAccountVisibilityPolicyTest {
    private fun game(account: String, appId: String, platform: GamePlatform = GamePlatform.STEAM) =
        StoreLibraryGame(
            id = platform.name + ":" + account + ":" + appId,
            accountId = account,
            platform = platform,
            title = "Juego " + appId,
            platformGameId = appId,
            artworkUrl = ""
        )

    private val games = listOf(
        game("steam-a", "440"),
        game("steam-b", "440"), // same Steam game, separate account ownership
        game("steam-b", "570"),
        game("epic-1", "epic-1", GamePlatform.EPIC_GAMES)
    )

    @Test fun activeSteamAccountSeesOnlyItsOwnSteamLibraryAndEpicRemainsIndependent() {
        assertEquals(
            listOf("STEAM:steam-a:440", "EPIC_GAMES:epic-1:epic-1"),
            SteamAccountVisibilityPolicy.visibleGames(games, "steam-a").map { it.id }
        )
    }

    @Test fun switchingActiveAccountDoesNotMergeSteamLibraries() {
        assertEquals(
            listOf("STEAM:steam-b:440", "STEAM:steam-b:570", "EPIC_GAMES:epic-1:epic-1"),
            SteamAccountVisibilityPolicy.visibleGames(games, "steam-b").map { it.id }
        )
    }

    @Test fun noActiveSteamAccountFailClosedInsteadOfShowingOtherSteamCaches() {
        assertEquals(
            listOf("EPIC_GAMES:epic-1:epic-1"),
            SteamAccountVisibilityPolicy.visibleGames(games, null).map { it.id }
        )
    }

    @Test fun mismatchingOrBlankActiveAccountDoesNotLeakCachedSteamGames() {
        for (active in listOf("", "steam-c", " steam-a ")) {
            assertEquals(
                listOf("EPIC_GAMES:epic-1:epic-1"),
                SteamAccountVisibilityPolicy.visibleGames(games, active).map { it.id }
            )
        }
    }

    @Test fun missingLocalLibraryMeansDisconnectedNotAuthenticated() {
        assertEquals(
            SteamAccountSessionState.DISCONNECTED,
            SteamAccountVisibilityPolicy.sessionState(
                lastSyncedAtMillis = null, hasCachedGames = false,
                nowMillis = 2_000_000_000L
            )
        )
    }

    @Test fun cachedDataDoesNotProveLiveSteamSessionEvenWhenFresh() {
        assertEquals(
            SteamAccountSessionState.UNVERIFIED,
            SteamAccountVisibilityPolicy.sessionState(
                lastSyncedAtMillis = 1_900_000_000L, hasCachedGames = true,
                nowMillis = 2_000_000_000L
            )
        )
    }

    @Test fun oldCachedMetadataRequiresReauthenticationInsteadOfReportingOnline() {
        val month = 30L * 24 * 60 * 60 * 1000
        assertEquals(
            SteamAccountSessionState.REAUTHENTICATION_REQUIRED,
            SteamAccountVisibilityPolicy.sessionState(
                lastSyncedAtMillis = 2_000_000_000L - month - 1,
                hasCachedGames = true,
                nowMillis = 2_000_000_000L
            )
        )
    }

    @Test fun invalidFutureOrNegativeSyncTimeNeverReportsAuthenticatedSession() {
        for (time in listOf(-1L, 0L, 2_000_000_001L)) {
            assertEquals(
                SteamAccountSessionState.UNVERIFIED,
                SteamAccountVisibilityPolicy.sessionState(
                    lastSyncedAtMillis = time, hasCachedGames = true,
                    nowMillis = 2_000_000_000L
                )
            )
        }
    }

    @Test fun messagesDistinguishCachedLibrariesFromLoggedInState() {
        assertEquals(
            "Sin biblioteca Steam local; inicia sesión para sincronizar.",
            SteamAccountVisibilityPolicy.statusText(SteamAccountSessionState.DISCONNECTED)
        )
        assertEquals(
            "Biblioteca Steam en caché; sesión no verificada.",
            SteamAccountVisibilityPolicy.statusText(SteamAccountSessionState.UNVERIFIED)
        )
        assertEquals(
            "Biblioteca Steam antigua; vuelve a iniciar sesión para actualizarla.",
            SteamAccountVisibilityPolicy.statusText(SteamAccountSessionState.REAUTHENTICATION_REQUIRED)
        )
        assertFalse(
            SteamAccountVisibilityPolicy.statusText(SteamAccountSessionState.UNVERIFIED)
                .contains("CONECTADO")
        )
    }
}
