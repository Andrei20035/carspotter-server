package com.revio.server.features.comment.dto

import com.revio.server.core.serialization.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class CommentRequest(
    val commentText: String,
)