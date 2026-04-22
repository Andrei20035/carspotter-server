package com.carspotter.features.post.dto

import com.carspotter.core.serialization.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

// data/dto/request/CreatePostMetadata.kt
@Serializable
data class CreatePostMetadata(
    @Serializable(with = UUIDSerializer::class)
    val carModelId: UUID,
    val latitude: Double,
    val longitude: Double,
    val description: String? = null
)