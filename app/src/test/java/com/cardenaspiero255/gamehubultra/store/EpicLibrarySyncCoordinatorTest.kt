package com.cardenaspiero255.gamehubultra.store

import com.cardenaspiero255.gamehubultra.data.ConnectedGameAccount
import com.cardenaspiero255.gamehubultra.data.ConnectedGameAccountsStateRepository
import com.cardenaspiero255.gamehubultra.data.StoreLibraryGame
import com.cardenaspiero255.gamehubultra.data.StoreLibraryStateRepository
import com.cardenaspiero255.gamehubultra.domain.GamePlatform
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** CAR-58: credentials must be checked and remote fetch must succeed before persistence. */
class EpicLibrarySyncCoordinatorTest {
    private val accountA = "0123456789abcdef0123456789abcdef"
    private val accountB = "fedcba9876543210fedcba9876543210"

    private class Accounts : ConnectedGameAccountsStateRepository {
        val saved = mutableListOf<ConnectedGameAccount>()
        var writes = 0

        override fun accountsFlow(): Flow<List<ConnectedGameAccount>> = flowOf(saved)
        override fun activeAccountIdFlow(): Flow<String?> = flowOf(null)
        override suspend fun setActiveAccount(accountId: String?) = false
        override suspend fun upsert(
            platform: GamePlatform, displayName: String, publicId: String,
            alias: String?, avatarUrl: String?
        ): ConnectedGameAccount {
            writes++
            return saved.firstOrNull { it.platform == platform && it.publicId == publicId }
                ?: ConnectedGameAccount(
                    id = publicId, platform = platform, displayName = displayName, publicId = publicId
                ).also(saved::add)
        }
        override suspend fun updatePublicMetadata(
            accountId: String, alias: String?, avatarUrl: String?
        ) = false
        override suspend fun remove(accountId: String) { saved.removeAll { it.id == accountId } }
    }

    private class Library : StoreLibraryStateRepository {
        val games = mutableListOf<StoreLibraryGame>()
        var writes = 0
        override fun getAll(): List<StoreLibraryGame> = games.toList()
        override fun replaceForAccount(accountId: String, games: List<StoreLibraryGame>) {
            writes++
            this.games.removeAll { it.accountId == accountId }
            this.games.addAll(games)
        }
        override fun removeForAccount(accountId: String) {
            games.removeAll { it.accountId == accountId }
        }
    }

    private fun credentials(id: String) = EpicCredentials(
        accessToken = "ephemeral-token",
        refreshToken = "unused-refresh-token",
        accountId = id,
        displayName = "Epic tester",
        expiresAt = Long.MAX_VALUE
    )

    @Test fun invalidSessionIsRejectedBeforeFetchAndPersistence() = runBlocking {
        val accounts = Accounts()
        val library = Library()
        var fetches = 0
        val coordinator = EpicLibrarySyncCoordinator(accounts, library) {
            fetches++
            emptyList()
        }
        assertFailsWith<IllegalArgumentException> {
            coordinator.sync(credentials("vanity-name"), null)
        }
        assertEquals(0, fetches)
        assertEquals(0, accounts.writes)
        assertEquals(0, library.writes)
    }

    @Test fun mismatchedSelectedAccountIsRejectedWithoutReadingOrMutatingCache() = runBlocking {
        val accounts = Accounts()
        val library = Library()
        var fetches = 0
        val coordinator = EpicLibrarySyncCoordinator(accounts, library) {
            fetches++
            emptyList()
        }
        assertFailsWith<IllegalArgumentException> {
            coordinator.sync(credentials("fedcba9876543210fedcba9876543210"), accountA)
        }
        assertEquals(0, fetches)
        assertEquals(0, accounts.writes)
        assertEquals(0, library.writes)
    }

    @Test fun unverifiableTargetCannotRefreshAnotherAccount() = runBlocking {
        val accounts = Accounts()
        val library = Library()
        var fetches = 0
        val coordinator = EpicLibrarySyncCoordinator(accounts, library) {
            fetches++
            emptyList()
        }
        assertFailsWith<IllegalArgumentException> {
            coordinator.sync(credentials(accountA), "nickname")
        }
        assertEquals(0, fetches)
        assertEquals(0, accounts.writes)
        assertEquals(0, library.writes)
    }

    @Test fun failedRemoteFetchPreservesPriorAccountAndLibraryMetadata() = runBlocking {
        val accounts = Accounts()
        val library = Library()
        accounts.saved += ConnectedGameAccount(
            id = accountA, platform = GamePlatform.EPIC_GAMES,
            displayName = "Existing Epic", publicId = accountA
        )
        library.games += StoreLibraryGame(
            id = "epic:$accountA:old-game", accountId = accountA,
            platform = GamePlatform.EPIC_GAMES, title = "Cached", platformGameId = "old-game",
            artworkUrl = ""
        )
        val previous = library.getAll()
        val coordinator = EpicLibrarySyncCoordinator(accounts, library) {
            throw IllegalStateException("Epic metadata endpoint unavailable")
        }
        assertFailsWith<IllegalStateException> { coordinator.sync(credentials(accountA), accountA) }
        assertEquals(0, accounts.writes)
        assertEquals(0, library.writes)
        assertEquals(previous, library.getAll())
        assertEquals("Existing Epic", accounts.saved.single().displayName)
    }

    @Test fun authorizedNewConnectionStoresPublicMetadataNotAccessToken() = runBlocking {
        val accounts = Accounts()
        val library = Library()
        val coordinator = EpicLibrarySyncCoordinator(accounts, library) { token ->
            assertEquals("ephemeral-token", token)
            listOf(EpicLibraryGame("game-1", "First game", "https://example.com/cover"))
        }
        assertEquals(1, coordinator.sync(credentials(accountA), null))
        assertEquals(1, accounts.writes)
        assertEquals(1, library.writes)
        assertEquals(accountA, accounts.saved.single().publicId)
        assertEquals("game-1", library.getAll().single().platformGameId)
        assertTrue(accounts.saved.single().toString().contains("Epic tester"))
        assertTrue(accounts.saved.single().toString().contains("ephemeral-token").not())
        assertTrue(library.getAll().single().toString().contains("ephemeral-token").not())
    }

    @Test fun authorizedRefreshOnlyReplacesItsOwnAccountsLibrary() = runBlocking {
        val accounts = Accounts()
        val library = Library()
        val other = StoreLibraryGame(
            id = "epic:$accountB:other", accountId = accountB,
            platform = GamePlatform.EPIC_GAMES, title = "Other account",
            platformGameId = "other", artworkUrl = ""
        )
        library.games += other
        library.games += other.copy(
            id = "epic:$accountA:old", accountId = accountA, platformGameId = "old"
        )
        val coordinator = EpicLibrarySyncCoordinator(accounts, library) {
            listOf(EpicLibraryGame("new", "New game", ""))
        }
        assertEquals(1, coordinator.sync(credentials(accountA), accountA))
        assertEquals(
            listOf("other"),
            library.getAll().filter { it.accountId == accountB }.map { it.platformGameId }
        )
        assertEquals(
            listOf("new"),
            library.getAll().filter { it.accountId == accountA }.map { it.platformGameId }
        )
    }

    @Test fun expiredEpicSessionIsRejectedBeforeAnyRemoteRequestOrCacheMutation() = runBlocking {
        val accounts = Accounts()
        val library = Library()
        val old = StoreLibraryGame(
            id = "epic:$accountA:cached", accountId = accountA,
            platform = GamePlatform.EPIC_GAMES, title = "Cached",
            platformGameId = "cached", artworkUrl = ""
        )
        library.games += old
        var fetches = 0
        val coordinator = EpicLibrarySyncCoordinator(accounts, library) {
            fetches++
            emptyList()
        }
        assertFailsWith<IllegalArgumentException> {
            coordinator.sync(credentials(accountA).copy(expiresAt = 0L), accountA)
        }
        assertEquals(0, fetches)
        assertEquals(0, accounts.writes)
        assertEquals(0, library.writes)
        assertEquals(listOf(old), library.getAll())
    }

    @Test fun successfulEmptyAuthorizedEpicLibraryClearsOnlySelectedAccountsCache() = runBlocking {
        val accounts = Accounts()
        val library = Library()
        val target = StoreLibraryGame(
            id = "epic:$accountA:old", accountId = accountA,
            platform = GamePlatform.EPIC_GAMES, title = "Old",
            platformGameId = "old", artworkUrl = ""
        )
        val unrelated = target.copy(
            id = "epic:$accountB:other", accountId = accountB, platformGameId = "other"
        )
        library.games += target
        library.games += unrelated
        val coordinator = EpicLibrarySyncCoordinator(accounts, library) { emptyList() }

        assertEquals(0, coordinator.sync(credentials(accountA), accountA))
        assertEquals(listOf(unrelated), library.getAll())
        assertEquals(1, library.writes)
    }

    @Test fun revokedLibraryPermissionLeavesPreviousAccountDataUntouched() = runBlocking {
        val accounts = Accounts()
        val library = Library()
        val cached = StoreLibraryGame(
            id = "epic:$accountA:licensed", accountId = accountA,
            platform = GamePlatform.EPIC_GAMES, title = "Cached title",
            platformGameId = "licensed", artworkUrl = ""
        )
        library.games += cached
        val coordinator = EpicLibrarySyncCoordinator(accounts, library) {
            throw SecurityException("Epic user consent revoked")
        }

        assertFailsWith<SecurityException> {
            coordinator.sync(credentials(accountA), accountA)
        }
        assertEquals(listOf(cached), library.getAll())
        assertEquals(0, accounts.writes)
        assertEquals(0, library.writes)
    }

    @Test fun emptyBearerTokenIsNotSentToMetadataEndpoint() = runBlocking {
        val accounts = Accounts()
        val library = Library()
        var fetches = 0
        val coordinator = EpicLibrarySyncCoordinator(accounts, library) {
            fetches++
            emptyList()
        }
        assertFailsWith<IllegalArgumentException> {
            coordinator.sync(credentials(accountA).copy(accessToken = ""), null)
        }
        assertEquals(0, fetches)
        assertEquals(0, accounts.writes)
        assertEquals(0, library.writes)
    }
}
