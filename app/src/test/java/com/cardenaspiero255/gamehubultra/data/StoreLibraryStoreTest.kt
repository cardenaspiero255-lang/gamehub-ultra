package com.cardenaspiero255.gamehubultra.data

import com.cardenaspiero255.gamehubultra.domain.GamePlatform
import kotlin.test.Test
import kotlin.test.assertEquals

class StoreLibraryStoreTest {
    @Test
    fun replacementOwnsGamesUnderRequestedAccount() {
        val games = normalizeStoreLibraryReplacement(
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

        assertEquals(listOf("account-a"), games.map { it.accountId })
    }
}
