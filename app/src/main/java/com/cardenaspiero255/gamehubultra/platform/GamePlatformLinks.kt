package com.cardenaspiero255.gamehubultra.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.cardenaspiero255.gamehubultra.data.ConnectedGameAccount
import com.cardenaspiero255.gamehubultra.domain.GameAccountValidation
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
        if (!GameAccountValidation.isValidPublicId(account.platform, account.publicId)) return false
        val uri = when (account.platform) {
            GamePlatform.STEAM -> {
                val id = account.publicId.trim()
                if (id.matches(Regex("[0-9]{10,20}"))) {
                    Uri.parse("https://steamcommunity.com/profiles/$id")
                } else {
                    Uri.parse("https://steamcommunity.com/id/$id")
                }
            }
            GamePlatform.EPIC_GAMES -> return false
        }
        return openUri(context, uri)
    }

    private fun openUri(context: Context, uri: Uri): Boolean = try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
