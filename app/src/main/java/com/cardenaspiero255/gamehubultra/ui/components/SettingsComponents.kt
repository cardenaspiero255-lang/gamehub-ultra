package com.cardenaspiero255.gamehubultra.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardenaspiero255.gamehubultra.R
import com.cardenaspiero255.gamehubultra.data.ConnectedGameAccount
import com.cardenaspiero255.gamehubultra.data.ConnectedGameAccountsStateRepository
import com.cardenaspiero255.gamehubultra.data.StoreLibraryStateRepository
import com.cardenaspiero255.gamehubultra.data.StoreLibraryStore
import com.cardenaspiero255.gamehubultra.domain.GameAccountValidation
import com.cardenaspiero255.gamehubultra.domain.GamePlatform
import com.cardenaspiero255.gamehubultra.platform.GamePlatformLinks
import com.cardenaspiero255.gamehubultra.store.StoreConnectionActivity
import com.cardenaspiero255.gamehubultra.ui.theme.GameHubUiTokens
import kotlinx.coroutines.launch

@Composable
internal fun ConnectedAccountsCard(
    accountsRepository: ConnectedGameAccountsStateRepository,
    onStoreConnectionChanged: () -> Unit
) {
    val context = LocalContext.current
    val store = accountsRepository
    val storeLibraryRepository: StoreLibraryStateRepository = remember(context) {
        StoreLibraryStore(context)
    }
    val accounts by store.accountsFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    val activeAccountId by store.activeAccountIdFlow().collectAsStateWithLifecycle(initialValue = null)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var platformName by rememberSaveable { mutableStateOf(GamePlatform.STEAM.name) }
    var displayName by rememberSaveable { mutableStateOf("") }
    var publicId by rememberSaveable { mutableStateOf("") }
    var alias by rememberSaveable { mutableStateOf("") }
    var avatarUrl by rememberSaveable { mutableStateOf("") }
    var browserError by rememberSaveable { mutableStateOf(false) }
    val connectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            browserError = false
            onStoreConnectionChanged()
        }
    }

    val platform = GamePlatform.valueOf(platformName)
    val profileIdSupported =
        GameAccountValidation.isValidPublicId(platform, publicId)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("connected_accounts")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                stringResource(R.string.accounts_title),
                style = MaterialTheme.typography.titleLarge
            )
            Text(stringResource(R.string.accounts_subtitle))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        browserError = false
                        connectionLauncher.launch(
                            StoreConnectionActivity.newIntent(
                                context,
                                GamePlatform.STEAM
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.accounts_steam_login))
                }
                Button(
                    onClick = {
                        browserError = false
                        connectionLauncher.launch(
                            StoreConnectionActivity.newIntent(
                                context,
                                GamePlatform.EPIC_GAMES
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.accounts_epic_login))
                }
            }
            TextButton(
                onClick = {
                    platformName = GamePlatform.STEAM.name
                    displayName = ""
                    publicId = ""
                    alias = ""
                    avatarUrl = ""
                    showAddDialog = true
                }
            ) {
                Text(stringResource(R.string.accounts_add))
            }

            accounts.forEach { account ->
                ConnectedAccountRow(
                    account = account,
                    active = account.id == activeAccountId,
                    onActivate = {
                        scope.launch { store.setActiveAccount(account.id) }
                    },
                    onRemove = {
                        scope.launch {
                            store.remove(account.id)
                            storeLibraryRepository.removeForAccount(account.id)
                            onStoreConnectionChanged()
                        }
                    },
                    onSync = {
                        connectionLauncher.launch(
                            StoreConnectionActivity.newIntent(
                                context,
                                account.platform
                            )
                        )
                    },
                    onOpen = {
                        if (!GamePlatformLinks.openPublicProfile(context, account)) {
                            browserError = true
                        }
                    }
                )
            }

            if (accounts.isEmpty()) {
                Text(
                    stringResource(R.string.accounts_empty),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (browserError) {
                Text(
                    stringResource(R.string.accounts_browser_failed),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = {
                Text(stringResource(R.string.accounts_add_title, platform.title))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        GamePlatform.entries.forEach { item ->
                            TextButton(
                                onClick = {
                                    platformName = item.name
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    if (platform == item) "✓ " + item.title else item.title
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.accounts_display_name)) }
                    )
                    OutlinedTextField(
                        value = alias,
                        onValueChange = { alias = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.accounts_alias)) }
                    )
                    OutlinedTextField(
                        value = avatarUrl,
                        onValueChange = { avatarUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.accounts_avatar_url)) }
                    )
                    OutlinedTextField(
                        value = publicId,
                        onValueChange = {
                            publicId = it
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.accounts_public_id)) }
                    )
                    if (!profileIdSupported) {
                        Text(
                            stringResource(R.string.accounts_public_id_invalid),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = displayName.isNotBlank() && publicId.isNotBlank() && profileIdSupported,
                    onClick = {
                        scope.launch {
                            store.upsert(platform, displayName, publicId, alias, avatarUrl)
                            displayName = ""
                            publicId = ""
                            showAddDialog = false
                        }                    }
                ) {
                    Text(stringResource(R.string.accounts_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }
}

@Composable
internal fun ConnectedAccountRow(
    account: ConnectedGameAccount,
    active: Boolean,
    onActivate: () -> Unit,
    onRemove: () -> Unit,
    onOpen: () -> Unit,
    onSync: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text((account.alias?.takeIf(String::isNotBlank) ?: account.displayName) +
                    if (active) " · ACTIVA" else "")
                Text(
                    (account.platform.title + " · " + account.publicId) +
                        (account.avatarUrl?.let { " · Avatar público configurado" } ?: ""),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            TextButton(onClick = onActivate) {
                Text(if (active) "ACTIVA" else "ACTIVAR")
            }
            TextButton(onClick = onSync) {
                Text("SYNC")
            }
            if (account.platform == GamePlatform.STEAM) {
                TextButton(onClick = onOpen) {
                    Text(stringResource(R.string.accounts_open))
                }
            }
            TextButton(onClick = onRemove) {
                Text(stringResource(R.string.remove_game))
            }
        }
    }
}

@Composable
internal fun SettingsScreen(
    modifier: Modifier,
    accountsRepository: ConnectedGameAccountsStateRepository,
    onStoreConnectionChanged: () -> Unit,
    onClearOptimizationMemory: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag("settings_scroll")
            .padding(GameHubUiTokens.compactHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(GameHubUiTokens.compactSectionSpacing)
    ) {
        Text(
            stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineSmall
        )
        ConnectedAccountsCard(
            accountsRepository = accountsRepository,
            onStoreConnectionChanged = onStoreConnectionChanged
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(R.string.optimization_memory_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(stringResource(R.string.optimization_memory_description))
                TextButton(onClick = onClearOptimizationMemory) {
                    Text(stringResource(R.string.optimization_memory_clear))
                }
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(R.string.language),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(stringResource(R.string.language_value))
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(R.string.about),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(stringResource(R.string.about_text))
                Text(
                    stringResource(R.string.limitations),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(stringResource(R.string.limitations_text))
            }
        }
    }
}

