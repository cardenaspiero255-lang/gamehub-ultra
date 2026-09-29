package com.cardenaspiero255.gamehubultra.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

internal enum class GameHubWideNavDestination { HOME, LIBRARY, PROFILE, SETTINGS }

internal fun selectedWideNavDestination(
    selectedTab: Int,
    settingsOpen: Boolean,
    profileOpen: Boolean
): GameHubWideNavDestination = when {
    settingsOpen -> GameHubWideNavDestination.SETTINGS
    profileOpen -> GameHubWideNavDestination.PROFILE
    selectedTab == 1 -> GameHubWideNavDestination.LIBRARY
    else -> GameHubWideNavDestination.HOME
}

/** Navigation rail for wide GameHub layouts. */
@Composable
internal fun GameHubWideNavigationRail(
    selectedTab: Int,
    settingsOpen: Boolean,
    profileOpen: Boolean,
    onHome: () -> Unit,
    onLibrary: () -> Unit,
    onProfile: () -> Unit,
    onSettings: () -> Unit
) {
    val selectedDestination = selectedWideNavDestination(selectedTab, settingsOpen, profileOpen)
    NavigationRail(containerColor = MaterialTheme.colorScheme.background) {
        NavigationRailItem(
            selected = selectedDestination == GameHubWideNavDestination.HOME,
            onClick = onHome,
            icon = { Text("⌂") },
            label = { Text("Inicio") },
            modifier = Modifier.testTag("nav_inicio")
                .semantics { contentDescription = "nav_inicio" }
        )
        NavigationRailItem(
            selected = selectedDestination == GameHubWideNavDestination.LIBRARY,
            onClick = onLibrary,
            icon = { Text("▦") },
            label = { Text("Biblioteca") },
            modifier = Modifier.testTag("nav_biblioteca")
                .semantics { contentDescription = "nav_biblioteca" }
        )
        NavigationRailItem(
            selected = selectedDestination == GameHubWideNavDestination.PROFILE,
            onClick = onProfile,
            icon = { Text("◎") },
            label = { Text("Perfil") },
            modifier = Modifier.testTag("nav_perfil")
                .semantics { contentDescription = "nav_perfil" }
        )
        NavigationRailItem(
            selected = selectedDestination == GameHubWideNavDestination.SETTINGS,
            onClick = onSettings,
            icon = { Text("⚙") },
            label = { Text("Ajustes") },
            modifier = Modifier.testTag("nav_ajustes")
                .semantics { contentDescription = "nav_ajustes" }
        )
    }
}
