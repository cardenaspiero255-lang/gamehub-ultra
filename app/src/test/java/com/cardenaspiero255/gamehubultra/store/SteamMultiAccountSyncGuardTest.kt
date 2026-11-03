package com.cardenaspiero255.gamehubultra.store

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.cardenaspiero255.gamehubultra.domain.GamePlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** CAR-57: a shared Steam WebView session must not overwrite a different account. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SteamMultiAccountSyncGuardTest {
    private val steamA = "76561198000000001"
    private val steamB = "76561198000000002"

    @Test fun aNewExplicitConnectionAcceptsAnyAuthenticatedNumericAccount() {
        assertEquals(
            SteamSyncAuthorization.NEW_CONNECTION,
            SteamMultiAccountSyncGuard.authorize(null, steamA)
        )
    }

    @Test fun explicitlySelectedAccountCanSynchronizeItsOwnSession() {
        assertEquals(
            SteamSyncAuthorization.MATCHED_ACCOUNT,
            SteamMultiAccountSyncGuard.authorize(steamA, steamA)
        )
    }

    @Test fun sessionFromAnotherSteamAccountIsRejectedBeforeAnyPersistence() {
        assertEquals(
            SteamSyncAuthorization.ACCOUNT_MISMATCH,
            SteamMultiAccountSyncGuard.authorize(steamA, steamB)
        )
    }

    @Test fun invalidOrMissingSessionIdentityNeverMeansSignedIn() {
        for (session in listOf("", "not-an-id", "76561198000000001;token=secret", "7656119800")) {
            assertEquals(
                SteamSyncAuthorization.INVALID_SESSION,
                SteamMultiAccountSyncGuard.authorize(steamA, session)
            )
        }
    }

    @Test fun vanityTargetCannotBeClaimedToBeAuthenticatedByNumericId() {
        for (target in listOf("", "my-vanity-name", " 76561198000000001 ", "7656119800")) {
            assertEquals(
                SteamSyncAuthorization.UNVERIFIABLE_TARGET,
                SteamMultiAccountSyncGuard.authorize(target, steamA)
            )
        }
    }

    @Test fun explicitResyncIntentCarriesOnlyTargetPublicSteamId() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val intent = StoreConnectionActivity.newIntent(
            context,
            GamePlatform.STEAM,
            expectedSteamId = steamA
        )
        assertEquals(
            steamA,
            intent.getStringExtra(StoreConnectionActivity.EXTRA_EXPECTED_STEAM_ID)
        )
        assertNull(intent.getStringExtra("password"))
        assertNull(intent.getStringExtra("steamLoginSecure"))
    }

    @Test fun ordinaryConnectIntentHasNoTargetAndEpicDoesNotClaimSteamIdentity() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val steam = StoreConnectionActivity.newIntent(context, GamePlatform.STEAM)
        val epic = StoreConnectionActivity.newIntent(context, GamePlatform.EPIC_GAMES)
        assertNull(steam.getStringExtra(StoreConnectionActivity.EXTRA_EXPECTED_STEAM_ID))
        assertNull(epic.getStringExtra(StoreConnectionActivity.EXTRA_EXPECTED_STEAM_ID))
    }
}
