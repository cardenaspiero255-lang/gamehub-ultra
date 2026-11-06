package com.cardenaspiero255.gamehubultra.ui.components

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cardenaspiero255.gamehubultra.GameInfo
import com.cardenaspiero255.gamehubultra.data.StoreLibraryGame
import com.cardenaspiero255.gamehubultra.domain.GamePlatform
import com.cardenaspiero255.gamehubultra.ui.library.state.LibraryLocalFilter
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UnifiedStoreLibrarySectionComposeTest {
    @get:Rule val composeRule = createComposeRule()

    private fun cached(
        source: GamePlatform, account: String, appId: String, title: String
    ) = StoreLibraryGame(
        id = source.name + ":" + account + ":" + appId,
        accountId = account,
        platform = source,
        title = title,
        platformGameId = appId,
        artworkUrl = ""
    )

    @Test fun localAndBothStoresAreShownAsPossibleMatchWithoutDiscardingOrigin() {
        composeRule.setContent {
            UnifiedStoreLibrarySection(
                installedGames = listOf(GameInfo("com.re4", "Resident Evil 4")),
                cachedStoreGames = listOf(
                    cached(GamePlatform.STEAM, "s1", "570", "Resident Evil 4"),
                    cached(GamePlatform.EPIC_GAMES, "e1", "epic-570", "resident evil 4")
                ),
                favoritePackages = setOf("com.re4"),
                recentPackages = listOf("com.re4"),
                query = "",
                localFilter = LibraryLocalFilter.ALL
            )
        }

        composeRule.onNodeWithText("Coincidencia de título · origen independiente").assertExists()
        composeRule.onNodeWithText("Steam").performClick()
        composeRule.onNodeWithText("Caché local · sesión no verificada").assertExists()
        composeRule.onNodeWithText("Instalados").performClick()
        composeRule.onNodeWithText("Aplicación local").assertExists()
    }

    @Test fun offlineCacheRemainsVisibleWithoutAnyInstalledAppsAndSourceFiltersRemainIsolated() {
        composeRule.setContent {
            UnifiedStoreLibrarySection(
                installedGames = emptyList(),
                cachedStoreGames = listOf(
                    cached(GamePlatform.STEAM, "s1", "570", "Steam-only game"),
                    cached(GamePlatform.EPIC_GAMES, "e1", "e570", "Epic-only game")
                ),
                favoritePackages = emptySet(),
                recentPackages = emptyList(),
                query = "",
                localFilter = LibraryLocalFilter.ALL
            )
        }

        composeRule.onNodeWithText("Epic").performClick()
        composeRule.onNodeWithText("Steam-only game").assertDoesNotExist()
        composeRule.onNodeWithText("Epic-only game").assertExists()
        composeRule.onNodeWithText("Steam").performClick()
        composeRule.onNodeWithText("Epic-only game").assertDoesNotExist()
        composeRule.onNodeWithText("Steam-only game").assertExists()
        composeRule.onNodeWithText("Todos").performClick()
    }

    @Test fun favoritesWithOnlyRemoteCopiesDoesNotClaimStoredLicensesAreLocalFavorites() {
        composeRule.setContent {
            UnifiedStoreLibrarySection(
                installedGames = emptyList(),
                cachedStoreGames = listOf(
                    cached(GamePlatform.STEAM, "s1", "570", "Saved only")
                ),
                favoritePackages = setOf("com.nonexistent"),
                recentPackages = listOf("com.nonexistent"),
                query = "Saved",
                localFilter = LibraryLocalFilter.FAVORITES
            )
        }
        composeRule.onNodeWithText(
            "Sin juegos para estos filtros; los datos locales permanecen guardados."
        ).assertExists()
    }
}
