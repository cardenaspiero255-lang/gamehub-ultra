package com.cardenaspiero255.gamehubultra.data

import com.cardenaspiero255.gamehubultra.domain.GamePlatform
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AccountPrivacyRecoveryTest {
    private fun account(id: String, platform: GamePlatform = GamePlatform.STEAM) =
        ConnectedGameAccount(id, platform, "A name containing access_token=TOPSECRET", "public-$id")

    private fun game(id: String, accountId: String, platform: GamePlatform = GamePlatform.STEAM) =
        StoreLibraryGame(id, accountId, platform, "A title", id, "https://img.test/?token=TOPSECRET")

    @Test fun invalidAndUnknownAccountsCannotTriggerDestructiveChanges() = runBlocking {
        val accounts = Mockito.mock(ConnectedGameAccountsStateRepository::class.java)
        val library = Mockito.mock(StoreLibraryStateRepository::class.java)
        Mockito.`when`(accounts.accountsFlow()).thenReturn(flowOf(listOf(account("keep"))))
        val service = AccountPrivacyRecovery(accounts, library)

        assertFalse(service.disconnect("  "))
        assertFalse(service.disconnect("unknown"))
        Mockito.verifyNoInteractions(library)
        Mockito.verify(accounts, Mockito.never()).remove("unknown")
    }

    @Test fun purgesAccountScopedCacheBeforeDetachingAccountMetadata() = runBlocking {
        val accounts = Mockito.mock(ConnectedGameAccountsStateRepository::class.java)
        val library = Mockito.mock(StoreLibraryStateRepository::class.java)
        Mockito.`when`(accounts.accountsFlow()).thenReturn(flowOf(listOf(account("removed"), account("kept"))))
        val service = AccountPrivacyRecovery(accounts, library)

        assertTrue(service.disconnect("removed"))
        val ordered = Mockito.inOrder(library, accounts)
        ordered.verify(library).removeForAccount("removed")
        ordered.verify(accounts).remove("removed")
        Mockito.verify(library, Mockito.never()).removeForAccount("kept")
        Mockito.verify(accounts, Mockito.never()).remove("kept")
    }

    @Test fun failedCachePurgeNeverDetachesAccountOrPretendsItWasDeleted() = runBlocking {
        val accounts = Mockito.mock(ConnectedGameAccountsStateRepository::class.java)
        val library = Mockito.mock(StoreLibraryStateRepository::class.java)
        Mockito.`when`(accounts.accountsFlow()).thenReturn(flowOf(listOf(account("keep"))))
        Mockito.doThrow(IOException("disk full")).`when`(library).removeForAccount("keep")

        assertFailsWith<IOException> {
            AccountPrivacyRecovery(accounts, library).disconnect("keep")
        }
        Mockito.verify(accounts, Mockito.never()).remove("keep")
    }

    @Test fun explicitPrivacyExportOmitsUserContentTokensAndAccountIds() {
        val report = AccountPrivacyExport.create(
            accounts = listOf(
                account("steam-1"),
                account("epic-1", GamePlatform.EPIC_GAMES)
            ),
            cachedGames = listOf(game("g1", "steam-1"), game("g2", "epic-1", GamePlatform.EPIC_GAMES))
        )

        assertTrue(report.contains("GameHub Ultra"))
        assertTrue(report.contains("Steam"))
        assertTrue(report.contains("Epic Games"))
        assertTrue(report.contains("1 cuenta"))
        assertFalse(report.contains("TOPSECRET"))
        assertFalse(report.contains("access_token"))
        assertFalse(report.contains("refresh_token"))
        assertFalse(report.contains("steam-1"))
        assertFalse(report.contains("epic-1"))
        assertFalse(report.contains("https://"))
        assertFalse(report.contains("public-"))
    }

    @Test fun exportNeverClaimsCachedTitlesAreAuthenticatedAndIgnoresOrphanCache() {
        val report = AccountPrivacyExport.create(
            accounts = listOf(account("own")),
            cachedGames = listOf(game("one", "own"), game("orphan", "other"))
        )
        assertTrue(report.contains("1 título"))
        assertFalse(report.contains("2 títulos"))
        assertTrue(report.contains("no demuestra sesión autenticada"))
    }

    @Test fun oldFutureAndInvalidSyncTimestampsRequireReauthentication() {
        val now = 1_800_000_000_000L
        val old = now - 31L * 24 * 60 * 60 * 1000
        assertEquals(AccountRecoveryState.REAUTH_REQUIRED, AccountRecoveryPolicy.evaluate(old, true, now))
        assertEquals(AccountRecoveryState.REAUTH_REQUIRED, AccountRecoveryPolicy.evaluate(now + 1, true, now))
        assertEquals(AccountRecoveryState.REAUTH_REQUIRED, AccountRecoveryPolicy.evaluate(0, true, now))
        assertEquals(AccountRecoveryState.UNVERIFIED_CACHE, AccountRecoveryPolicy.evaluate(now, true, now))
        assertEquals(AccountRecoveryState.DISCONNECTED, AccountRecoveryPolicy.evaluate(null, false, now))
        assertEquals(AccountRecoveryState.UNVERIFIED_CACHE, AccountRecoveryPolicy.evaluate(null, true, now))
    }
}
