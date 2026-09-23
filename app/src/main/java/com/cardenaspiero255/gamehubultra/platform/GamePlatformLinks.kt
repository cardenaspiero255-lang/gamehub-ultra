package com.cardenaspiero255.gamehubultra.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.cardenaspiero255.gamehubultra.data.ConnectedGameAccount
import com.cardenaspiero255.gamehubultra.domain.GamePlatform

object GamePlatformLinks {
    private val steamNumericProfileId = Regex("[0-9]{10,20}")
    private val steamVanityProfileId = Regex("[A-Za-z0-9_-]{1,64}")

    fun openOfficialLogin(context: Context, platform: GamePlatform): Boolean {
        val uri = when (platform) {
            GamePlatform.STEAM -> Uri.parse("https://store.steampowered.com/login/")
            GamePlatform.EPIC_GAMES -> Uri.parse("https://www.epicgames.com/id/login")
        }
        return openUri(context, uri)
    }

    fun isPublicProfileIdSupported(account: ConnectedGameAccount): Boolean =
        account.platform == GamePlatform.STEAM &&
            isSteamProfileIdSupported(account.publicId)

    private fun isSteamProfileIdSupported(publicId: String): Boolean {
        val id = publicId.trim()
        return id.matches(steamNumericProfileId) || id.matches(steamVanityProfileId)
    }

    fun openPublicProfile(context: Context, account: ConnectedGameAccount): Boolean {
        val publicId = account.publicId.trim()
        val uri = when (account.platform) {
            GamePlatform.STEAM -> when {
                publicId.matches(steamNumericProfileId) ->
                    Uri.parse("https://steamcommunity.com/profiles/$publicId")
                publicId.matches(steamVanityProfileId) ->
                    Uri.parse("https://steamcommunity.com/id/$publicId")
                else -> null
            }
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
