package com.cardenaspiero255.gamehubultra.quicksettings
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.cardenaspiero255.gamehubultra.MainActivity
import com.cardenaspiero255.gamehubultra.R

class GameHubQuickSettingsTileService : TileService() {
    override fun onStartListening() { super.onStartListening(); updateTile() }
    override fun onClick() {
        super.onClick()
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("gamehubultra://performance")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(this, 0, launchIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(launchIntent)
        }
    }
    private fun updateTile() {
        qsTile?.apply {
            label = getString(R.string.qs_gamehub_label)
            icon = Icon.createWithResource(this@GameHubQuickSettingsTileService, R.drawable.ic_qs_gamehub)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) contentDescription = getString(R.string.qs_gamehub_description)
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }
}