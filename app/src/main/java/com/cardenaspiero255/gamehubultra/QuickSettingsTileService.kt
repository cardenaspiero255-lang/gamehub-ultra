package com.cardenaspiero255.gamehubultra

import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.cardenaspiero255.gamehubultra.platform.InputAccessoryDetector

class QuickSettingsTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        startActivityAndCollapse(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        )
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val inputs = InputAccessoryDetector.detect(this)
        tile.label = getString(R.string.qs_gamehub_title)
        tile.contentDescription = getString(
            R.string.qs_gamehub_description,
            inputs.total,
            if (inputs.externalAudio) getString(R.string.qs_audio_connected)
            else getString(R.string.qs_audio_not_connected)
        )
        tile.state = Tile.STATE_ACTIVE
        tile.icon = Icon.createWithResource(this, R.drawable.ic_gamehub_tile)
        tile.updateTile()
    }
}
