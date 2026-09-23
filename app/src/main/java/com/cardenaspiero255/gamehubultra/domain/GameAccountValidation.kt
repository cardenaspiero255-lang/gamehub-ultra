package com.cardenaspiero255.gamehubultra.domain

object GameAccountValidation {
    private val steamId64 = Regex("[0-9]{17}")
    private val steamVanity = Regex("[A-Za-z0-9_-]{2,32}")
    private val epicPublicId = Regex("[A-Za-z0-9._-]{2,64}")

    fun isValidPublicId(platform: GamePlatform, value: String): Boolean {
        val id = value.trim()
        if (id.isEmpty()) return false
        return when (platform) {
            GamePlatform.STEAM -> isSteamId64(id) || steamVanity.matches(id)
            GamePlatform.EPIC_GAMES -> epicPublicId.matches(id)
        }
    }

    fun isSteamId64(value: String): Boolean =
        steamId64.matches(value.trim())
}
