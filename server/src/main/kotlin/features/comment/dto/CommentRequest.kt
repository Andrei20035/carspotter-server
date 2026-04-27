package com.carspotter.features.comment.dto

import com.carspotter.core.serialization.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class CommentRequest(
    val commentText: String,
)