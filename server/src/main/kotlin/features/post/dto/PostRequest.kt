package com.carspotter.features.post.dto

import com.carspotter.core.serialization.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class PostRequest(
    @Serializable(with = UUIDSerializer::class)
    val carModelId: UUID,
    val imagePath: String,
    val description: String? = null,
    val latitude: Double,
    val longitude: Double,
)

fun PostRequest.addId(userId: UUID) = CreatePostDTO(
    userId = userId,
    carModelId = this.carModelId,
    imageBytes = this.imagePath.toByteArray(),
    description = this.description,
    latitude = this.latitude,
    longitude = this.longitude,
    contentType = this.imagePath.substringAfterLast('.')
)
