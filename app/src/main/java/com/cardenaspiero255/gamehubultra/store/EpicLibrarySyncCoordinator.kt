package com.cardenaspiero255.gamehubultra.store

import com.cardenaspiero255.gamehubultra.data.ConnectedGameAccountsStateRepository
import com.cardenaspiero255.gamehubultra.data.StoreLibraryGame
import com.cardenaspiero255.gamehubultra.data.StoreLibraryStateRepository
import com.cardenaspiero255.gamehubultra.domain.GamePlatform

/**
 * CAR-58: a remote fetch and identity check must succeed before any local account/cache write.
 * Access tokens are passed only to a scoped, in-memory library fetcher, never to persistence.
 */
internal class EpicLibrarySyncCoordinator(
    private val accounts: ConnectedGameAccountsStateRepository,
    private val library: StoreLibraryStateRepository,
    private val fetchLibrary: suspend (String) -> List<EpicLibraryGame>
) {
    suspend fun sync(credentials: EpicCredentials, expectedAccountId: String?): Int {
        val authorization = EpicIntegrationPolicy.authorizeAccount(
            expectedAccountId, credentials.accountId
        )
        require(
            authorization == EpicAccountAuthorization.NEW_CONNECTION ||
                authorization == EpicAccountAuthorization.MATCHED_ACCOUNT
        ) { "Epic account identity could not be verified" }
        require(credentials.accessToken.isNotBlank()) { "Missing Epic access token" }
        require(credentials.expiresAt > System.currentTimeMillis()) {
            "Epic access token has expired"
        }
        // Remote failure must leave pre-existing public account/library metadata intact.
        val imported = fetchLibrary(credentials.accessToken)
        val account = accounts.upsert(
            platform = GamePlatform.EPIC_GAMES,
            displayName = credentials.displayName.ifBlank { "Epic Games" },
            publicId = credentials.accountId
        )
        val games = imported.map {
            StoreLibraryGame(
                id = "epic:" + account.id + ":" + it.appName,
                accountId = account.id,
                platform = GamePlatform.EPIC_GAMES,
                title = it.title,
                platformGameId = it.appName,
                artworkUrl = it.artworkUrl
            )
        }
        library.replaceForAccount(account.id, games)
        return games.size
    }
}
