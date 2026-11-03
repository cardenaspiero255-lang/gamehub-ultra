package com.cardenaspiero255.gamehubultra.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import android.app.Activity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cardenaspiero255.gamehubultra.data.ConnectedGameAccount
import com.cardenaspiero255.gamehubultra.data.ConnectedGameAccountsStateRepository
import com.cardenaspiero255.gamehubultra.data.StoreLibraryGame
import com.cardenaspiero255.gamehubultra.data.StoreLibraryStateRepository
import com.cardenaspiero255.gamehubultra.domain.GamePlatform
import kotlinx.coroutines.flow.flowOf
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EpicAccountsStatusComposeTest {
    @get:Rule val composeRule = createComposeRule()
    private var hostActivity: Activity? = null

    private fun renderEpicAccount(lastSync: Long?, cached: Boolean) {
        val account = ConnectedGameAccount(
            id = "epic-ui-id",
            platform = GamePlatform.EPIC_GAMES,
            displayName = "Epic tester",
            publicId = "0123456789abcdef0123456789abcdef",
            lastSyncedAtMillis = lastSync
        )
        val accounts = Mockito.mock(ConnectedGameAccountsStateRepository::class.java)
        val library = Mockito.mock(StoreLibraryStateRepository::class.java)
        Mockito.`when`(accounts.accountsFlow()).thenReturn(flowOf(listOf(account)))
        Mockito.`when`(accounts.activeAccountIdFlow()).thenReturn(flowOf(account.id))
        val games = if (cached) listOf(
            StoreLibraryGame(
                id = "epic:epic-ui-id:app-1",
                accountId = "epic-ui-id",
                platform = GamePlatform.EPIC_GAMES,
                title = "Epic title",
                platformGameId = "app-1",
                artworkUrl = ""
            )
        ) else emptyList()
        Mockito.`when`(library.getAll()).thenReturn(games)
        composeRule.setContent {
            hostActivity = LocalContext.current as? Activity
            ConnectedAccountsCard(
                accountsRepository = accounts,
                storeLibraryRepository = library,
                onStoreConnectionChanged = {}
            )
        }
    }

    @Test fun selectedEpicAccountSyncLaunchesBoundActivityWithoutLeakingTokens() {
        renderEpicAccount(lastSync = null, cached = false)
        composeRule.onNodeWithText("SYNC").performClick()
        val host = checkNotNull(hostActivity)
        val started = checkNotNull(Shadows.shadowOf(host).nextStartedActivity)
        assertEquals(
            "0123456789abcdef0123456789abcdef",
            started.getStringExtra("expected_epic_id")
        )
        assertEquals(GamePlatform.EPIC_GAMES.name, started.getStringExtra("platform"))
        assertEquals(null, started.getStringExtra("access_token"))
        assertEquals(null, started.getStringExtra("refresh_token"))
    }

    @Test fun noEpicCacheRendersDisconnectedState() {
        renderEpicAccount(lastSync = null, cached = false)
        composeRule.onNodeWithText(
            "Epic: sin biblioteca local; inicia sesión para sincronizar."
        ).assertExists()
    }

    @Test fun recentlySyncedEpicCacheShowsUnverifiedNotConnected() {
        renderEpicAccount(lastSync = System.currentTimeMillis(), cached = true)
        composeRule.onNodeWithText(
            "Epic: biblioteca local en caché; sesión no verificada."
        ).assertExists()
    }

    @Test fun oldEpicCacheAsksForFreshLoginWithoutErasingGames() {
        renderEpicAccount(lastSync = 1L, cached = true)
        composeRule.onNodeWithText(
            "Epic: caché local antigua; vuelve a iniciar sesión para actualizar."
        ).assertExists()
    }
}
