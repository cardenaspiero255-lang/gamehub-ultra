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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EpicAccountBoundIntentTest {
    private val epicId = "0123456789abcdef0123456789abcdef"
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun explicitEpicResyncCarriesOnlyPublicTargetId() {
        val intent = StoreConnectionActivity.newIntent(
            context, GamePlatform.EPIC_GAMES, expectedEpicId = epicId
        )
        assertEquals(
            epicId, intent.getStringExtra(StoreConnectionActivity.EXTRA_EXPECTED_EPIC_ID)
        )
        assertEquals(GamePlatform.EPIC_GAMES.name,
            intent.getStringExtra(StoreConnectionActivity.EXTRA_PLATFORM))
        assertNull(intent.getStringExtra("access_token"))
        assertNull(intent.getStringExtra("refresh_token"))
        assertNull(intent.getStringExtra("authorizationCode"))
    }

    @Test fun firstEpicConnectionDoesNotClaimExistingAccountIdentity() {
        val intent = StoreConnectionActivity.newIntent(context, GamePlatform.EPIC_GAMES)
        assertNull(intent.getStringExtra(StoreConnectionActivity.EXTRA_EXPECTED_EPIC_ID))
    }

    @Test fun SteamIntentCannotCarryAnEpicAccountTarget() {
        val intent = StoreConnectionActivity.newIntent(
            context, GamePlatform.STEAM, expectedEpicId = epicId
        )
        assertNull(intent.getStringExtra(StoreConnectionActivity.EXTRA_EXPECTED_EPIC_ID))
    }
}
