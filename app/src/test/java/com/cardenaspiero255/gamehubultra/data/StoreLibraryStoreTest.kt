package com.cardenaspiero255.gamehubultra.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.cardenaspiero255.gamehubultra.domain.GamePlatform
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class StoreLibraryStoreTest {
    private lateinit var store: StoreLibraryStore

    @BeforeTest
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences(
            "gamehub_ultra_store_library",
            Context.MODE_PRIVATE
        ).edit().clear().commit()
        store = StoreLibraryStore(context)
    }

    @Test
    fun replacementOwnsGamesUnderRequestedAccount() {
        store.replaceForAccount(
            "account-a",
            listOf(
                StoreLibraryGame(
                    id = "steam:wrong:10",
                    accountId = "wrong-account",
                    platform = GamePlatform.STEAM,
                    title = "Game",
                    platformGameId = "10",
                    artworkUrl = ""
                )
            )
        )

        assertEquals(listOf("account-a"), store.getAll().map { it.accountId })

        store.removeForAccount("account-a")
        assertEquals(emptyList(), store.getAll())
    }
}
