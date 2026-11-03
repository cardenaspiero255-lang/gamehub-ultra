package com.cardenaspiero255.gamehubultra.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.cardenaspiero255.gamehubultra.data.ConnectedGameAccount
import com.cardenaspiero255.gamehubultra.data.ConnectedGameAccountsStateRepository
import com.cardenaspiero255.gamehubultra.data.StoreLibraryGame
import com.cardenaspiero255.gamehubultra.data.StoreLibraryStateRepository
import com.cardenaspiero255.gamehubultra.domain.GamePlatform
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SteamAccountsStatusComposeTest {
    @get:Rule val composeRule = createComposeRule()

    private fun renderAccount(
        lastSync: Long?,
        hasCachedGame: Boolean
    ) {
        val account = ConnectedGameAccount(
            id = "account-steam-ui-test",
            platform = GamePlatform.STEAM,
            displayName = "Steam player",
            publicId = "76561198000000001",
            lastSyncedAtMillis = lastSync
        )
        val accounts = Mockito.mock(ConnectedGameAccountsStateRepository::class.java)
        val library = Mockito.mock(StoreLibraryStateRepository::class.java)
        Mockito.`when`(accounts.accountsFlow()).thenReturn(flowOf(listOf(account)))
        Mockito.`when`(accounts.activeAccountIdFlow()).thenReturn(flowOf(account.id))
        val games = if (hasCachedGame) listOf(
            StoreLibraryGame(
                id = "steam:account-steam-ui-test:440",
                accountId = account.id,
                platform = GamePlatform.STEAM,
                title = "Team Fortress",
                platformGameId = "440",
                artworkUrl = ""
            )
        ) else emptyList()
        Mockito.`when`(library.getAll()).thenReturn(games)
        composeRule.setContent {
            ConnectedAccountsCard(
                accountsRepository = accounts,
                storeLibraryRepository = library,
                onStoreConnectionChanged = {}
            )
        }
    }

    @Test fun emptySteamCacheDoesNotClaimConnectedSession() {
        renderAccount(lastSync = null, hasCachedGame = false)
        composeRule.onNodeWithText(
            "Sin biblioteca Steam local; inicia sesión para sincronizar."
        ).assertExists()
    }

    @Test fun recentSteamCacheShowsUnverifiedSessionInsteadOfConnected() {
        renderAccount(lastSync = System.currentTimeMillis(), hasCachedGame = true)
        composeRule.onNodeWithText(
            "Biblioteca Steam en caché; sesión no verificada."
        ).assertExists()
    }

    @Test fun staleSteamCachePromptsReauthentication() {
        renderAccount(lastSync = 1L, hasCachedGame = true)
        composeRule.onNodeWithText(
            "Biblioteca Steam antigua; vuelve a iniciar sesión para actualizarla."
        ).assertExists()
    }
}
