package com.cardenaspiero255.gamehubultra.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import com.cardenaspiero255.gamehubultra.data.ConnectedGameAccount
import com.cardenaspiero255.gamehubultra.data.ConnectedGameAccountsStateRepository
import com.cardenaspiero255.gamehubultra.data.StoreLibraryStateRepository
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Regression: SettingsScreen must really compose the Sentinel panel.
 * This exercises the SettingsComponents.kt call site for JaCoCo patch coverage. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsScreenSentinelIntegrationTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun settingsScreenContainsSentinelInTheScrollableSettingsHierarchy() {
        val accounts = Mockito.mock(ConnectedGameAccountsStateRepository::class.java)
        val library = Mockito.mock(StoreLibraryStateRepository::class.java)
        Mockito.`when`(accounts.accountsFlow())
            .thenReturn(flowOf(emptyList<ConnectedGameAccount>()))
        Mockito.`when`(accounts.activeAccountIdFlow())
            .thenReturn(flowOf<String?>(null))

        composeRule.setContent {
            SettingsScreen(
                modifier = Modifier,
                accountsRepository = accounts,
                storeLibraryRepository = library,
                onStoreConnectionChanged = {},
                onClearOptimizationMemory = {},
                playerName = "Ultra",
                onPlayerNameChanged = {}
            )
        }
        composeRule.onNodeWithTag("settings_scroll").assertExists()
        composeRule.onNodeWithTag("sentinel_status_panel", useUnmergedTree = true)
            .performScrollTo()
            .assertExists()
    }
}
