package com.carspotter.features.post.dto

import com.carspotter.features.post.Post
import com.carspotter.core.serialization.InstantSerializer
import com.carspotter.core.serialization.UUIDSerializer
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.*

@Serializable
data class PostDTO(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID = UUID.randomUUID(),
    @Serializable(with = UUIDSerializer::class)
    val userId: UUID,
    @Serializable(with = UUIDSerializer::class)
    val carModelId: UUID? = null,
    val customBrand: String? = null,
    val customModel: String? = null,
    val imagePath: String,
    val description: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant,
    @Serializable(with = InstantSerializer::class)
    val updatedAt: Instant,
)

fun Post.toDTO() = PostDTO(
    id = this.id,
    userId = this.userId,
    carModelId = this.carModelId,
    customBrand = this.customBrand,
    customModel = this.customModel,
    imagePath = this.imagePath,
    description = this.description,
    latitude = this.latitude,
    longitude = this.longitude,
    createdAt = this.createdAt,
    updatedAt = this.updatedAt
)

fun List<Post>.toDTO(): List<PostDTO> {
    return map { it.toDTO() }
}