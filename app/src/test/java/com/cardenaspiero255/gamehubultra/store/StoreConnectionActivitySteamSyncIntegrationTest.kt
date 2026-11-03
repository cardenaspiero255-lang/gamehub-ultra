package com.cardenaspiero255.gamehubultra.store

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.cardenaspiero255.gamehubultra.data.ConnectedGameAccountsStore
import com.cardenaspiero255.gamehubultra.data.StoreLibraryStore
import com.cardenaspiero255.gamehubultra.domain.GamePlatform
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Executes the real Activity authorization gate and persistence boundary, not a mock guard. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StoreConnectionActivitySteamSyncIntegrationTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val steamA = "76561198000000001"
    private val steamB = "76561198000000002"

    private fun activity(expected: String? = null): StoreConnectionActivity =
        Robolectric.buildActivity(
            StoreConnectionActivity::class.java,
            StoreConnectionActivity.newIntent(context, GamePlatform.STEAM, expected)
        ).create().get()

    private fun syncResult(
        id: String,
        appId: Int = 440
    ) = SteamSyncResult(
        steamId = id,
        displayName = "Steam test",
        games = listOf(SteamGameResult(appId, "Game " + appId, ""))
    )

    private suspend fun assertRejectedWithoutMutation(
        expected: String?,
        session: String
    ) {
        val accounts = ConnectedGameAccountsStore(context)
        val library = StoreLibraryStore(context)
        val beforeAccounts = accounts.accountsFlow().first()
        val beforeLibrary = library.getAll()
        val subject = activity(expected)
        subject.syncSteam(syncResult(session))
        assertTrue(subject.isFinishing)
        assertEquals(beforeAccounts, accounts.accountsFlow().first())
        assertEquals(beforeLibrary, library.getAll())
    }

    @Test fun invalidAuthenticatedSessionNeverChangesAccountOrLibrary() = runBlocking {
        assertRejectedWithoutMutation(steamA, " 76561198000000001 ")
    }

    @Test fun mismatchedWebViewSteamSessionNeverOverwritesSelectedAccount() = runBlocking {
        assertRejectedWithoutMutation(steamA, steamB)
    }

    @Test fun unverifiableVanityTargetRejectsAValidAuthenticatedSteamSession() = runBlocking {
        assertRejectedWithoutMutation("my-vanity-profile", steamA)
    }

    @Test fun newExplicitConnectionPersistsOnlyItsOwnAuthenticatedLibrary() = runBlocking {
        val id = "76561198000000015"
        val activity = activity()
        activity.syncSteam(syncResult(id, appId = 480))
        assertTrue(activity.isFinishing)
        val stored = ConnectedGameAccountsStore(context).accountsFlow().first()
            .first { it.platform == GamePlatform.STEAM && it.publicId == id }
        val ownLibrary = StoreLibraryStore(context).getAll()
            .filter { it.accountId == stored.id }
        assertEquals(1, ownLibrary.size)
        assertEquals("480", ownLibrary.single().platformGameId)
    }

    @Test fun matchingSelectedSteamIdIsAllowedToRefreshOnlyItsOwnLibrary() = runBlocking {
        val id = "76561198000000016"
        val beforeLibrary = StoreLibraryStore(context).getAll()
        val activity = activity(id)
        activity.syncSteam(syncResult(id, appId = 570))
        assertTrue(activity.isFinishing)
        val stored = ConnectedGameAccountsStore(context).accountsFlow().first()
            .first { it.platform == GamePlatform.STEAM && it.publicId == id }
        val afterLibrary = StoreLibraryStore(context).getAll()
        assertEquals(
            listOf("570"),
            afterLibrary.filter { it.accountId == stored.id }.map { it.platformGameId }
        )
        assertEquals(
            beforeLibrary.filter { it.accountId != stored.id },
            afterLibrary.filter { it.accountId != stored.id },
            "Refreshing one Steam account must not erase other accounts' libraries"
        )
    }
}
