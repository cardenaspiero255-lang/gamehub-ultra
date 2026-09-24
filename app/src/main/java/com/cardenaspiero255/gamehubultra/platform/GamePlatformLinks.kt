package com.cardenaspiero255.gamehubultra.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.cardenaspiero255.gamehubultra.data.ConnectedGameAccount
import com.cardenaspiero255.gamehubultra.domain.GamePlatform
import com.cardenaspiero255.gamehubultra.domain.GameAccountValidation
import com.cardenaspiero255.gamehubultra.store.StoreConnectionActivity

object GamePlatformLinks {

    fun openOfficialLogin(context: Context, platform: GamePlatform): Boolean {
        return try {
            context.startActivity(StoreConnectionActivity.newIntent(context, platform))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    fun isPublicProfileIdSupported(account: ConnectedGameAccount): Boolean =
        isPublicProfileIdSupported(account.platform, account.publicId)

    fun isPublicProfileIdSupported(platform: GamePlatform, publicId: String): Boolean =
        platform == GamePlatform.STEAM && isSteamProfileIdSupported(publicId)

    private fun isSteamProfileIdSupported(publicId: String): Boolean {
        val id = publicId.trim()
        return GameAccountValidation.isValidPublicId(GamePlatform.STEAM, id)
    }

    fun openPublicProfile(context: Context, account: ConnectedGameAccount): Boolean {
        val publicId = account.publicId.trim()
        val uri = when (account.platform) {
            GamePlatform.STEAM -> when {
                GameAccountValidation.isSteamId64(publicId) ->
                    Uri.parse("https://steamcommunity.com/profiles/$publicId")
                GameAccountValidation.isValidPublicId(GamePlatform.STEAM, publicId) ->
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
