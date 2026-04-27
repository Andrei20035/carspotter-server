package com.carspotter.features.comment.dto

import features.comment.Comment
import com.carspotter.core.serialization.InstantSerializer
import com.carspotter.core.serialization.UUIDSerializer
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.*

@Serializable
data class CommentDTO(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    @Serializable(with = UUIDSerializer::class)
    val userId: UUID,
    @Serializable(with = UUIDSerializer::class)
    val postId: UUID,
    val username: String,
    val profilePicturePath: String?,
    val commentText: String,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant,
    @Serializable(with = InstantSerializer::class)
    val updatedAt: Instant,
)

fun Comment.toDTO() = CommentDTO(
    id = this.id,
    userId = this.userId,
    postId = this.postId,
    username = this.username,
    profilePicturePath = this.profilePicturePath,
    commentText = this.commentText,
    createdAt = this.createdAt,
    updatedAt = this.updatedAt,
)