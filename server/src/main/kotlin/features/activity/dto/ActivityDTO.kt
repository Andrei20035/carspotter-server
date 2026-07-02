package com.carspotter.features.activity.dto

import com.carspotter.core.serialization.InstantSerializer
import com.carspotter.core.serialization.UUIDSerializer
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

/**
 * Flat DTO for every activity item, discriminated by [type]. Fields not relevant to a given
 * [type] are null. Kept flat (rather than a polymorphic sealed hierarchy) to stay simple to
 * consume on Android without a custom class discriminator.
 *
 * [id] carries a type prefix (e.g. "like:<uuid>") so it stays unique across item types and is
 * stable for use as a LazyColumn key.
 */
@Serializable
data class ActivityItemDTO(
    val type: String,
    val id: String,
    @Serializable(with = UUIDSerializer::class)
    val actorUserId: UUID? = null,
    val actorUsername: String? = null,
    val actorAvatarUrl: String? = null,
    @Serializable(with = UUIDSerializer::class)
    val postId: UUID? = null,
    val postThumbnailUrl: String? = null,
    val brand: String? = null,
    val model: String? = null,
    val commentText: String? = null,
    val placesMoved: Int? = null,
    val streakDays: Int? = null,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant,
)

@Serializable
data class ActivityResponseDTO(
    val weeklySpotScore: Int,
    val todayInteractions: Int,
    val items: List<ActivityItemDTO>,
)
