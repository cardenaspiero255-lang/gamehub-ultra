package com.cardenaspiero255.gamehubultra.domain

object GameAccountValidation {
    private val steamNumeric = Regex("[0-9]{10,20}")
    private val steamVanity = Regex("[A-Za-z0-9_-]{2,32}")
    private val epicPublicId = Regex("[A-Za-z0-9._-]{2,64}")

    fun isValidPublicId(platform: GamePlatform, value: String): Boolean {
        val id = value.trim()
        if (id.isEmpty()) return false
        return when (platform) {
            GamePlatform.STEAM -> steamNumeric.matches(id) || steamVanity.matches(id)
            GamePlatform.EPIC_GAMES -> epicPublicId.matches(id)
        }
    }
}
