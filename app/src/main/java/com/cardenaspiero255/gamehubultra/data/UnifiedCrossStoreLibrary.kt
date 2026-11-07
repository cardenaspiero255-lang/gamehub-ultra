package com.cardenaspiero255.gamehubultra.data

import com.cardenaspiero255.gamehubultra.GameInfo
import com.cardenaspiero255.gamehubultra.domain.GamePlatform
import java.util.Locale

/**
 * CAR-59. Origin is a composite identifier, never a title or an untrusted cache row ID.
 * No Steam/Epic credentials, account session or remote service is needed for offline browsing.
 */
internal enum class UnifiedLibrarySource { INSTALLED, STEAM, EPIC_GAMES }

internal data class UnifiedLibraryIdentity(
    val source: UnifiedLibrarySource,
    val accountId: String?,
    val sourceId: String
)

internal data class UnifiedLibraryCopy(
    val identity: UnifiedLibraryIdentity,
    val title: String,
    val installed: Boolean = false,
    val favorite: Boolean = false,
    val recent: Boolean = false
)

internal data class UnifiedLibraryEntry(
    val key: String,
    val title: String,
    val copies: List<UnifiedLibraryCopy>,
    /**
     * Matching display names are merely suggestions, not verified equivalent purchases.
     * All source/account/game identities remain separate in [copies].
     */
    val possibleCrossStoreMatch: Boolean
)

internal data class UnifiedLibraryFilters(
    val query: String = "",
    val source: UnifiedLibrarySource? = null,
    val installedOnly: Boolean = false,
    val favoritesOnly: Boolean = false,
    val recentOnly: Boolean = false
)

/** Read-only projection over local app discovery and persisted store metadata. */
internal object UnifiedCrossStoreLibrary {
    private val spaces = Regex("\\s+")

    private fun titleKey(title: String): String =
        title.trim().replace(spaces, " ").lowercase(Locale.ROOT)

    fun build(
        installedGames: List<GameInfo>,
        storeGames: List<StoreLibraryGame>,
        favoritePackages: Set<String> = emptySet(),
        recentPackages: List<String> = emptyList(),
        filters: UnifiedLibraryFilters = UnifiedLibraryFilters()
    ): List<UnifiedLibraryEntry> {
        val local = installedGames.asSequence().filter {
            it.label.isNotBlank() && it.packageName.isNotBlank()
        }.map { game ->
            UnifiedLibraryCopy(
                identity = UnifiedLibraryIdentity(
                    UnifiedLibrarySource.INSTALLED, null, game.packageName
                ),
                title = game.label,
                installed = true,
                favorite = game.packageName in favoritePackages,
                recent = game.packageName in recentPackages
            )
        }
        val cached = storeGames.asSequence().filter {
            it.title.isNotBlank() && it.accountId.isNotBlank() && it.platformGameId.isNotBlank()
        }.map { game ->
            UnifiedLibraryCopy(
                identity = UnifiedLibraryIdentity(
                    source = when (game.platform) {
                        GamePlatform.STEAM -> UnifiedLibrarySource.STEAM
                        GamePlatform.EPIC_GAMES -> UnifiedLibrarySource.EPIC_GAMES
                    },
                    accountId = game.accountId,
                    sourceId = game.platformGameId
                ),
                title = game.title
            )
        }
        val query = filters.query.trim()
        return (local + cached)
            .distinctBy { it.identity }
            .filter { copy ->
                (filters.source == null || copy.identity.source == filters.source) &&
                    (!filters.installedOnly || copy.installed) &&
                    (!filters.favoritesOnly || copy.favorite) &&
                    (!filters.recentOnly || copy.recent) &&
                    (query.isBlank() ||
                        copy.title.contains(query, ignoreCase = true) ||
                        copy.identity.sourceId.contains(query, ignoreCase = true))
            }
            .groupBy { copy -> titleKey(copy.title) }
            .filterKeys { it.isNotBlank() }
            .map { (key, copies) ->
                val ordered = copies.sortedWith(
                    compareBy<UnifiedLibraryCopy> { it.identity.source.ordinal }
                        .thenBy { it.identity.accountId.orEmpty() }
                        .thenBy { it.identity.sourceId }
                )
                UnifiedLibraryEntry(
                    key = key,
                    title = ordered.first().title.trim(),
                    copies = ordered,
                    possibleCrossStoreMatch = ordered.map { it.identity.source }.distinct().size > 1
                )
            }
            .sortedWith(compareBy<UnifiedLibraryEntry> { it.key }.thenBy { it.title })
    }
}
