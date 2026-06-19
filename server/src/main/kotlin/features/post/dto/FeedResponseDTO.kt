package com.carspotter.features.post.dto

import com.carspotter.core.serialization.InstantSerializer
import com.carspotter.core.serialization.UUIDSerializer
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

/**
 * Opaque cursor for keyset feed pagination. Points at the last post of the current page;
 * the next request returns posts strictly older than this (created_at, id) pair.
 */
@Serializable
data class FeedCursorDTO(
    @Serializable(with = InstantSerializer::class)
    val lastCreatedAt: Instant,
    @Serializable(with = UUIDSerializer::class)
    val lastPostId: UUID,
)

@Serializable
data class FeedResponseDTO(
    val posts: List<PostDTO>,
    val nextCursor: FeedCursorDTO? = null,
    val hasMore: Boolean,
)
