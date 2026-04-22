package com.carspotter.features.comment

import java.time.Instant
import java.util.UUID

data class Comment(
    val id: UUID = UUID.randomUUID(),
    val userId: UUID,
    val postId: UUID,
    val commentText: String,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
)