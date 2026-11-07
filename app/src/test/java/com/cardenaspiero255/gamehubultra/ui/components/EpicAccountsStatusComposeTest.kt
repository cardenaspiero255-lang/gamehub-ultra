package com.cardenaspiero255.gamehubultra.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import android.app.Activity
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.cardenaspiero255.gamehubultra.R
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import androidx.activity.compose.LocalActivity
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

    private data class RenderedAccount(
        val accounts: ConnectedGameAccountsStateRepository,
        val library: StoreLibraryStateRepository,
        val changeEvents: AtomicInteger
    )

    private fun renderEpicAccount(lastSync: Long?, cached: Boolean): RenderedAccount {
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
        val changeEvents = AtomicInteger(0)
        composeRule.setContent {
            hostActivity = LocalActivity.current
            ConnectedAccountsCard(
                accountsRepository = accounts,
                storeLibraryRepository = library,
                onStoreConnectionChanged = { changeEvents.incrementAndGet() }
            )
        }
        return RenderedAccount(accounts, library, changeEvents)
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

    @Test fun removingEpicAccountDeletesItsAccountScopedCacheAndNotifiesConsumers() {
        val rendered = renderEpicAccount(lastSync = 1L, cached = true)
        val context = ApplicationProvider.getApplicationContext<Context>()
        composeRule.onNodeWithText(context.getString(R.string.remove_game)).performClick()
        Mockito.verify(rendered.library, Mockito.never()).removeForAccount("epic-ui-id")
        composeRule.onNodeWithText("BORRAR DATOS LOCALES").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            rendered.changeEvents.get() == 1
        }
        runBlocking {
            Mockito.verify(rendered.accounts).remove("epic-ui-id")
        }
        Mockito.verify(rendered.library).removeForAccount("epic-ui-id")
        assertEquals(1, rendered.changeEvents.get())
    }

    @Test fun dismissingDeletionConfirmationPreservesAccountAndOfflineCache() {
        val rendered = renderEpicAccount(lastSync = 1L, cached = true)
        val context = ApplicationProvider.getApplicationContext<Context>()
        composeRule.onNodeWithText(context.getString(R.string.remove_game)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.close)).performClick()
        composeRule.waitForIdle()
        Mockito.verify(rendered.library, Mockito.never()).removeForAccount("epic-ui-id")
        runBlocking {
            Mockito.verify(rendered.accounts, Mockito.never()).remove("epic-ui-id")
        }
        assertEquals(0, rendered.changeEvents.get())
    }

    @Test fun staleEpicAccountOffersExplicitReauthenticationWithoutClaimingSession() {
        renderEpicAccount(lastSync = 1L, cached = true)
        composeRule.onNodeWithText("REAUTENTICAR").assertExists()
        composeRule.onNodeWithText(
            "Epic: caché local antigua; vuelve a iniciar sesión para actualizar."
        ).assertExists()
    }

    @Test fun activatingEpicAccountSelectsOnlyItsOwnIdentifier() {
        val rendered = renderEpicAccount(lastSync = null, cached = false)
        composeRule.onNodeWithText("ACTIVA").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            Mockito.mockingDetails(rendered.accounts).invocations.any {
                it.method.name == "setActiveAccount"
            }
        }
        runBlocking {
            Mockito.verify(rendered.accounts).setActiveAccount("epic-ui-id")
        }
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
