package com.cardenaspiero255.gamehubultra.domain

const val DEFAULT_ULTRA_PLAYER_NAME = "ejecutor3.0"
const val MAX_ULTRA_PLAYER_NAME_LENGTH = 32

fun normalizeUltraPlayerName(rawName: String): String =
    rawName
        .trim()
        .replace(Regex("""\s+"""), " ")
        .take(MAX_ULTRA_PLAYER_NAME_LENGTH)
        .ifBlank { DEFAULT_ULTRA_PLAYER_NAME }
