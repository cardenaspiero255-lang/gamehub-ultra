package com.cardenaspiero255.gamehubultra.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cardenaspiero255.gamehubultra.R

/**
 * In-app read-only entry point for Ultra Sentinel's trusted GitHub reports.
 * There is deliberately no Sentry token, direct privileged API access, fake
 * live statuses, or auto-merge button inside the Android application.
 */
@Composable
internal fun SentinelStatusCard(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("sentinel_status_panel")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(stringResource(R.string.sentinel_card_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.sentinel_card_description))
            Text(
                stringResource(R.string.sentinel_card_ci_status),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                stringResource(R.string.sentinel_card_disclaimer),
                style = MaterialTheme.typography.bodySmall
            )
            TextButton(
                onClick = {
                    uriHandler.openUri(
                        "https://github.com/cardenaspiero255-lang/gamehub-ultra/actions"
                    )
                }
            ) {
                Text(stringResource(R.string.sentinel_card_open_reviews))
            }
        }
    }
}
