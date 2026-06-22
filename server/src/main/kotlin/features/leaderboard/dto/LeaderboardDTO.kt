package com.carspotter.features.leaderboard.dto

import com.carspotter.core.serialization.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class LeaderboardEntryDTO(
    @Serializable(with = UUIDSerializer::class)
    val userId: UUID,
    val rank: Int,
    val username: String,
    val avatarUrl: String?,
    val spotScore: Int,
    val streakDays: Int,
)

@Serializable
data class CurrentUserStandingDTO(
    val entry: LeaderboardEntryDTO,
    /** Always KEEP in v1 — no historical rank snapshots yet. */
    val movement: String = "KEEP",
    val placesMoved: Int = 0,
)

@Serializable
data class LeaderboardResponseDTO(
    val currentUser: CurrentUserStandingDTO,
    val entries: List<LeaderboardEntryDTO>,
)
