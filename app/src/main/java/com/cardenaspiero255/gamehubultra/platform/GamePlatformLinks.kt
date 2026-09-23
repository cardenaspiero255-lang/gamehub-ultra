package com.cardenaspiero255.gamehubultra.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.cardenaspiero255.gamehubultra.data.ConnectedGameAccount
import com.cardenaspiero255.gamehubultra.domain.GamePlatform

object GamePlatformLinks {
    fun openOfficialLogin(context: Context, platform: GamePlatform): Boolean {
        val uri = when (platform) {
            GamePlatform.STEAM -> Uri.parse("https://store.steampowered.com/login/")
            GamePlatform.EPIC_GAMES -> Uri.parse("https://www.epicgames.com/id/login")
        }
        return openUri(context, uri)
    }

    fun openPublicProfile(context: Context, account: ConnectedGameAccount): Boolean {
        val uri = when (account.platform) {
            GamePlatform.STEAM ->
                account.publicId.takeIf { it.matches(Regex("[0-9]{10,20}")) }
                    ?.let { Uri.parse("https://steamcommunity.com/profiles/$it") }
            GamePlatform.EPIC_GAMES -> null
        } ?: return false
        return openUri(context, uri)
    }

    private fun openUri(context: Context, uri: Uri): Boolean = try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
