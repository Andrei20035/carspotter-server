package com.revio.server.features.auth.dto

import com.revio.server.features.auth.AuthProvider
import kotlinx.serialization.Serializable

@Serializable
data class DeletionContextDTO(
    val provider: AuthProvider,
    val postCount: Int,
    val likesReceived: Int,
    val leaderboardRank: Int?,
    val streakDays: Int,
    val accountAgeDays: Int,
)
