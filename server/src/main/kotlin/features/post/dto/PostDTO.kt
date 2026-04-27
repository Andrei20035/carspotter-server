package com.carspotter.features.post.dto

import com.carspotter.features.post.Post
import com.carspotter.core.serialization.InstantSerializer
import com.carspotter.core.serialization.UUIDSerializer
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

@Serializable
data class PostDTO(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    @Serializable(with = UUIDSerializer::class)
    val userId: UUID,
    val username: String,
    @Serializable(with = UUIDSerializer::class)
    val carModelId: UUID? = null,
    val brand: String,
    val model: String,
    val imageUrl: String,
    val caption: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant,
)

fun Post.toDTO(imageUrl: String) = PostDTO(
    id = this.id,
    userId = this.userId,
    username = this.username,
    carModelId = this.carModelId,
    brand = this.brand,
    model = this.model,
    imageUrl = imageUrl,
    caption = this.caption,
    latitude = this.latitude,
    longitude = this.longitude,
    createdAt = this.createdAt,
)
