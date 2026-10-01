package com.cardenaspiero255.gamehubultra.data

import com.cardenaspiero255.gamehubultra.domain.GamePlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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

    @Test
    fun replacementRejectsBlankOwningAccountId() {
        assertFailsWith<IllegalArgumentException> {
            normalizeStoreLibraryReplacement(
                "   ",
                listOf(
                    StoreLibraryGame(
                        id = "steam:10",
                        accountId = "account-a",
                        platform = GamePlatform.STEAM,
                        title = "Game",
                        platformGameId = "10",
                        artworkUrl = ""
                    )
                )
            )
        }
    }

    @Test
    fun replacementCanonicalizesOwningAccountId() {
        val games = normalizeStoreLibraryReplacement(
            "  account-a  ",
            listOf(
                StoreLibraryGame(
                    id = "steam:10",
                    accountId = "account-a",
                    platform = GamePlatform.STEAM,
                    title = "Game",
                    platformGameId = "10",
                    artworkUrl = ""
                )
            )
        )

        assertEquals(listOf("account-a"), games.map { it.accountId })
    }


    @Test
    fun replacementScopeUsesCanonicalOwningAccountId() {
        val retained = retainStoreLibraryOutsideAccount(
            "  account-a  ",
            listOf(
                StoreLibraryGame(
                    id = "steam:old",
                    accountId = "account-a",
                    platform = GamePlatform.STEAM,
                    title = "Old",
                    platformGameId = "old",
                    artworkUrl = ""
                ),
                StoreLibraryGame(
                    id = "steam:other",
                    accountId = "account-b",
                    platform = GamePlatform.STEAM,
                    title = "Other",
                    platformGameId = "other",
                    artworkUrl = ""
                )
            )
        )

        assertEquals(listOf("account-b"), retained.map { it.accountId })
    }

}
