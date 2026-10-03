package com.cardenaspiero255.gamehubultra.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cardenaspiero255.gamehubultra.domain.MAX_ULTRA_PLAYER_NAME_LENGTH
import com.cardenaspiero255.gamehubultra.domain.normalizeUltraPlayerName

@Composable
internal fun PlayerNameEditor(
    playerName: String,
    onPlayerNameChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var draft by rememberSaveable(playerName) { mutableStateOf(playerName) }
    val normalized = normalizeUltraPlayerName(draft)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it.take(MAX_ULTRA_PLAYER_NAME_LENGTH) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Tu nombre") },
            supportingText = {
                Text("Puedes usar cualquier nombre de hasta $MAX_ULTRA_PLAYER_NAME_LENGTH caracteres.")
            },
            singleLine = true
        )
        Button(
            onClick = {
                draft = normalized
                onPlayerNameChanged(normalized)
            },
            enabled = normalized != playerName,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Guardar nombre")
        }
    }
}
