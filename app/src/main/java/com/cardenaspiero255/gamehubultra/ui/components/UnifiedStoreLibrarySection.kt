package com.cardenaspiero255.gamehubultra.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cardenaspiero255.gamehubultra.GameInfo
import com.cardenaspiero255.gamehubultra.data.StoreLibraryGame
import com.cardenaspiero255.gamehubultra.data.UnifiedCrossStoreLibrary
import com.cardenaspiero255.gamehubultra.data.UnifiedLibraryFilters
import com.cardenaspiero255.gamehubultra.data.UnifiedLibrarySource
import com.cardenaspiero255.gamehubultra.ui.library.state.LibraryLocalFilter
import com.cardenaspiero255.gamehubultra.ui.theme.GameHubUiTokens

/**
 * CAR-59: offline read-only projection; never requests a remote login or claims live ownership.
 * Source chips do not mutate the actual installed-game launch carousel.
 */
@Composable
internal fun UnifiedStoreLibrarySection(
    installedGames: List<GameInfo>,
    cachedStoreGames: List<StoreLibraryGame>,
    favoritePackages: Set<String>,
    recentPackages: List<String>,
    query: String,
    localFilter: LibraryLocalFilter
) {
    var selectedSourceName by rememberSaveable { mutableStateOf("ALL") }
    val source = UnifiedLibrarySource.entries.firstOrNull {
        it.name == selectedSourceName
    }
    val groups = remember(
        installedGames, cachedStoreGames, favoritePackages, recentPackages,
        query, localFilter, source
    ) {
        UnifiedCrossStoreLibrary.build(
            installedGames = installedGames,
            storeGames = cachedStoreGames,
            favoritePackages = favoritePackages,
            recentPackages = recentPackages,
            filters = UnifiedLibraryFilters(
                query = query,
                source = source,
                favoritesOnly = localFilter == LibraryLocalFilter.FAVORITES,
                recentOnly = localFilter == LibraryLocalFilter.RECENT
            )
        )
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(GameHubUiTokens.compactCardPadding),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                "BIBLIOTECA UNIFICADA · ${groups.size} títulos",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "Instalados + Steam + Epic · datos de tiendas en caché, sesiones no verificadas",
                style = MaterialTheme.typography.bodySmall
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(
                    listOf(
                        "ALL" to "Todos",
                        UnifiedLibrarySource.INSTALLED.name to "Instalados",
                        UnifiedLibrarySource.STEAM.name to "Steam",
                        UnifiedLibrarySource.EPIC_GAMES.name to "Epic"
                    )
                ) { (sourceId, label) ->
                    FilterChip(
                        selected = selectedSourceName == sourceId,
                        onClick = { selectedSourceName = sourceId },
                        label = { Text(label) }
                    )
                }
            }
            if (groups.isEmpty()) {
                Text("Sin juegos para estos filtros; los datos locales permanecen guardados.")
            } else {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(groups, key = { it.key }) { group ->
                        Surface(
                            modifier = Modifier.size(width = 215.dp, height = 114.dp),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    group.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1
                                )
                                Text(
                                    group.copies.joinToString(" · ") { copy ->
                                        when (copy.identity.source) {
                                            UnifiedLibrarySource.INSTALLED -> "Instalado"
                                            UnifiedLibrarySource.STEAM -> "Steam"
                                            UnifiedLibrarySource.EPIC_GAMES -> "Epic"
                                        }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2
                                )
                                Text(
                                    if (group.possibleCrossStoreMatch) {
                                        "Coincidencia de título · origen independiente"
                                    } else if (group.copies.any { it.installed }) {
                                        "Aplicación local"
                                    } else {
                                        "Caché local · sesión no verificada"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
