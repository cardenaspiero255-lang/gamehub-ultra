package com.cardenaspiero255.gamehubultra.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.cardenaspiero255.gamehubultra.domain.GamePlatform
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StoreLibraryPrivacyRobolectricTest {
    private lateinit var context: Context
    private lateinit var library: StoreLibraryStore

    @Before fun before() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("gamehub_ultra_store_library", Context.MODE_PRIVATE)
            .edit().clear().commit()
        library = StoreLibraryStore(context)
    }

    private fun game(accountId: String, platformId: String) = StoreLibraryGame(
        id = "steam:$accountId:$platformId",
        accountId = accountId,
        platform = GamePlatform.STEAM,
        title = "Game $platformId",
        platformGameId = platformId,
        artworkUrl = ""
    )

    @Test fun disconnectPurgesOneAccountCacheAndPreservesOtherAccountsAfterReopen() {
        library.replaceForAccount("account-one", listOf(game("account-one", "1")))
        library.replaceForAccount("account-two", listOf(game("account-two", "2")))

        library.removeForAccount("account-one")

        val reopened = StoreLibraryStore(context)
        assertEquals(listOf("account-two"), reopened.getAll().map { it.accountId })
        assertEquals(listOf("2"), reopened.getAll().map { it.platformGameId })
    }

    @Test fun malformedCacheRecoversAsEmptyAndCanAcceptFreshMetadata() {
        context.getSharedPreferences("gamehub_ultra_store_library", Context.MODE_PRIVATE)
            .edit().putString("games_v1", "{invalid json").commit()
        assertTrue(library.getAll().isEmpty())
        library.replaceForAccount("account-one", listOf(game("account-one", "1")))
        assertEquals(1, StoreLibraryStore(context).getAll().size)
    }

    @Test fun malformedAccountlessMetadataCannotBeExposedInOfflineLibrary() {
        context.getSharedPreferences("gamehub_ultra_store_library", Context.MODE_PRIVATE)
            .edit().putString(
                "games_v1",
                """[{"id":"bad","accountId":"","platform":"STEAM","title":"Orphan","platformGameId":"1","artworkUrl":""}]"""
            ).commit()
        assertTrue(library.getAll().isEmpty())
    }
}
