package com.cardenaspiero255.gamehubultra.data

import com.cardenaspiero255.gamehubultra.GameInfo
import com.cardenaspiero255.gamehubultra.domain.GamePlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * CAR-59: do not confuse visually grouped cached titles with ownership or cross-store IDs.
 * These JVM tests require no network, Epic credentials or device.
 */
class UnifiedCrossStoreLibraryTest {
    private fun local(pkg: String, name: String) = GameInfo(pkg, name)
    private fun store(
        platform: GamePlatform,
        account: String,
        appId: String,
        title: String
    ) = StoreLibraryGame(
        id = platform.name + ":" + account + ":" + appId,
        platform = platform,
        accountId = account,
        platformGameId = appId,
        title = title,
        artworkUrl = ""
    )

    private val steam = store(GamePlatform.STEAM, "steam-a", "570", "Resident Evil 4")
    private val epic = store(GamePlatform.EPIC_GAMES, "epic-a", "re4-prod", "resident evil 4")

    @Test fun exactTitleOnlyGroupsPresentationWithoutErasingStoreOrigin() {
        val groups = UnifiedCrossStoreLibrary.build(
            installedGames = listOf(local("com.game.re4", "Resident Evil 4")),
            storeGames = listOf(steam, epic),
            favoritePackages = setOf("com.game.re4"),
            recentPackages = listOf("com.game.re4")
        )
        assertEquals(1, groups.size)
        val grouped = groups.single()
        assertEquals(3, grouped.copies.size)
        assertTrue(grouped.possibleCrossStoreMatch)
        assertEquals(
            setOf(UnifiedLibrarySource.INSTALLED, UnifiedLibrarySource.STEAM, UnifiedLibrarySource.EPIC_GAMES),
            grouped.copies.map { it.identity.source }.toSet()
        )
        assertEquals(
            setOf("com.game.re4", "570", "re4-prod"),
            grouped.copies.map { it.identity.sourceId }.toSet()
        )
        assertTrue(grouped.copies.first { it.identity.source == UnifiedLibrarySource.INSTALLED }.favorite)
        assertTrue(grouped.copies.first { it.identity.source == UnifiedLibrarySource.INSTALLED }.recent)
    }

    @Test fun punctuationAndEditionsAreNotAutomaticallyCombined() {
        val groups = UnifiedCrossStoreLibrary.build(
            installedGames = emptyList(),
            storeGames = listOf(
                steam,
                store(GamePlatform.EPIC_GAMES, "epic-a", "deluxe", "Resident Evil 4: Deluxe Edition"),
                store(GamePlatform.EPIC_GAMES, "epic-a", "remaster", "Resident Evil IV")
            )
        )
        assertEquals(3, groups.size)
        assertTrue(groups.all { it.copies.size == 1 && !it.possibleCrossStoreMatch })
    }

    @Test fun differentAccountsWithSameAppIdKeepDistinctOwnershipEntries() {
        val groups = UnifiedCrossStoreLibrary.build(
            emptyList(), listOf(
                steam,
                store(GamePlatform.STEAM, "steam-b", "570", "Resident Evil 4"),
                steam // Exact same source/account/id duplicated in cache; never show duplicate card.
            )
        )
        val entry = groups.single()
        assertEquals(2, entry.copies.size)
        assertEquals(setOf("steam-a", "steam-b"), entry.copies.map { it.identity.accountId }.toSet())
        assertFalse(entry.possibleCrossStoreMatch)
    }

    @Test fun sourceFilterNeverLeaksOtherProviderOrAccountLicenses() {
        val filtered = UnifiedCrossStoreLibrary.build(
            installedGames = listOf(local("com.game.re4", "Resident Evil 4")),
            storeGames = listOf(steam, epic),
            filters = UnifiedLibraryFilters(source = UnifiedLibrarySource.EPIC_GAMES)
        )
        assertEquals(1, filtered.size)
        assertEquals(1, filtered.single().copies.size)
        assertEquals("epic-a", filtered.single().copies.single().identity.accountId)
        assertEquals(UnifiedLibrarySource.EPIC_GAMES, filtered.single().copies.single().identity.source)
    }

    @Test fun installedFavoritesAndRecentsRequireActualLocalEvidence() {
        val local = local("com.game.re4", "Resident Evil 4")
        val data = listOf(steam, epic)
        for (filters in listOf(
            UnifiedLibraryFilters(installedOnly = true),
            UnifiedLibraryFilters(favoritesOnly = true),
            UnifiedLibraryFilters(recentOnly = true)
        )) {
            val groups = UnifiedCrossStoreLibrary.build(
                installedGames = listOf(local),
                storeGames = data,
                favoritePackages = setOf("com.game.re4"),
                recentPackages = listOf("com.game.re4"),
                filters = filters
            )
            assertEquals(1, groups.size)
            assertEquals(UnifiedLibrarySource.INSTALLED, groups.single().copies.single().identity.source)
        }
        assertTrue(
            UnifiedCrossStoreLibrary.build(
                installedGames = emptyList(),
                storeGames = data,
                favoritePackages = setOf("com.game.re4"),
                recentPackages = listOf("com.game.re4"),
                filters = UnifiedLibraryFilters(favoritesOnly = true)
            ).isEmpty()
        )
    }

    @Test fun cachedStoreMetadataRemainsBrowseableWithNoLocalDiscoveryOrConnectivity() {
        val groups = UnifiedCrossStoreLibrary.build(
            installedGames = emptyList(),
            storeGames = listOf(steam, epic),
            filters = UnifiedLibraryFilters(query = "  RESIDENT  ")
        )
        assertEquals(1, groups.size)
        assertEquals(2, groups.single().copies.size)
        assertTrue(groups.single().copies.all { !it.installed && !it.favorite && !it.recent })
    }

    @Test fun rejectsUnscopedMalformedCacheAndDoesNotAssumeAnAccountIsAuthenticated() {
        val groups = UnifiedCrossStoreLibrary.build(
            installedGames = emptyList(),
            storeGames = listOf(
                store(GamePlatform.STEAM, "", "570", "Resident Evil 4"),
                store(GamePlatform.EPIC_GAMES, "epic", "", "Resident Evil 4"),
                store(GamePlatform.EPIC_GAMES, "epic", "valid", "")
            )
        )
        assertTrue(groups.isEmpty())
    }

    @Test fun searchRecognizesProviderIdsAndSortsWithoutLosingEditionVariants() {
        val games = listOf(
            store(GamePlatform.STEAM, "s1", "b-42", "Zeta"),
            store(GamePlatform.EPIC_GAMES, "e1", "b-43", "Alpha")
        )
        assertEquals(
            listOf("Alpha", "Zeta"),
            UnifiedCrossStoreLibrary.build(emptyList(), games).map { it.title }
        )
        val filtered = UnifiedCrossStoreLibrary.build(
            emptyList(), games, filters = UnifiedLibraryFilters(query = " b-43 ")
        )
        assertEquals("Alpha", filtered.single().title)
    }

    @Test fun duplicateDisplayTitlesRemainExplicitlyUnverifiedPossibleMatches() {
        val groups = UnifiedCrossStoreLibrary.build(emptyList(), listOf(steam, epic))
        assertEquals(1, groups.size)
        assertTrue(groups.single().possibleCrossStoreMatch)
        assertEquals(2, groups.single().copies.size)
        // Grouping is a UI presentation only, not proof that Epic ownership was verified.
        assertTrue(groups.single().copies.none { it.installed })
    }
}
