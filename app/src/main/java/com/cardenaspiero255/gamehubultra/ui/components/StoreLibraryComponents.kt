package com.cardenaspiero255.gamehubultra.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cardenaspiero255.gamehubultra.data.StoreLibraryGame
import com.cardenaspiero255.gamehubultra.ui.theme.GameHubUiTokens

/** Compact entry point into the connected Steam/Epic library. */
@Composable
internal fun StoreLibrarySummary(
    games: List<StoreLibraryGame>,
    onOpenLibrary: () -> Unit
) {
    Card(
        onClick = onOpenLibrary,
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            contentDescription = "Abrir Biblioteca de tiendas"
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(GameHubUiTokens.compactCardPadding),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text("BIBLIOTECA DE TIENDAS", style = MaterialTheme.typography.titleMedium)
                Text(
                    storeLibrarySummaryText(games.size),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
            }
            Text("ABRIR  ›", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Connected-store games shown inside the library screen. */
@Composable
internal fun StoreLibrarySection(games: List<StoreLibraryGame>) {
    if (games.isEmpty()) return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(GameHubUiTokens.compactCardPadding),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "STEAM / EPIC · ${games.size}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(games.take(12), key = { it.id }) { game ->
                    Surface(
                        modifier = Modifier.size(width = 170.dp, height = 60.dp),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(
                            modifier = Modifier.padding(7.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(game.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                            Text(
                                game.platform.title + " · " + game.platformGameId,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                            Text("CONECTADO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

internal fun storeLibrarySummaryText(gameCount: Int): String =
    if (gameCount <= 0) {
        "Conecta Steam o Epic para sincronizar tus juegos dentro de Ultra."
    } else {
        "Steam + Epic sincronizados · $gameCount juego(s)"
    }
