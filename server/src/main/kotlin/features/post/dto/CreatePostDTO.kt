package com.carspotter.features.post.dto

import java.util.UUID

data class CreatePostDTO(
    val userId: UUID,
    val carModelId: UUID,
    val latitude: Double,
    val longitude: Double,
    val description: String?,
    val imageBytes: ByteArray,
    val contentType: String
)