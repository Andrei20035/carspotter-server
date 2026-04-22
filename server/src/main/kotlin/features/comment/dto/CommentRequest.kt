package com.carspotter.features.comment.dto

import com.carspotter.core.serialization.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class CommentRequest(
    @Serializable(with = UUIDSerializer::class)
    val postId: UUID,
    val commentText: String,
)