package com.cardenaspiero255.gamehubultra.domain

object GameAccountValidation {
    private const val STEAM_ID64_MIN = 76561197960265728L
    private const val STEAM_ID64_MAX = 76561202255233023L
    private val steamId64 = Regex("\\d{17}")
    private val steamVanity = Regex("[A-Za-z0-9_-]{2,32}")
    private val epicPublicId = Regex("[A-Za-z0-9._-]{2,64}")

    fun isValidPublicId(platform: GamePlatform, value: String): Boolean {
        val id = value.trim()
        if (id.isEmpty()) return false
        return when (platform) {
            GamePlatform.STEAM -> isSteamPublicId(id)
            GamePlatform.EPIC_GAMES -> epicPublicId.matches(id)
        }
    }

    private fun isSteamPublicId(value: String): Boolean {
        if (steamId64.matches(value)) return isSteamId64(value)
        return steamVanity.matches(value)
    }

    fun isSteamId64(value: String): Boolean {
        val id = value.trim()
        if (!steamId64.matches(id)) return false
        return id.toLongOrNull()?.let { it in STEAM_ID64_MIN..STEAM_ID64_MAX } == true
    }
}
