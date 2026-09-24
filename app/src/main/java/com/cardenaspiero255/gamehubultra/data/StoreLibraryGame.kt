package com.cardenaspiero255.gamehubultra.data

import com.cardenaspiero255.gamehubultra.domain.GamePlatform

data class StoreLibraryGame(
    val id: String,
    val accountId: String,
    val platform: GamePlatform,
    val title: String,
    val platformGameId: String,
    val artworkUrl: String
)
