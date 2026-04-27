package com.carspotter.features.post.dto

import java.util.UUID

data class CreatePostDTO(
    val authorId: UUID,
    val carModelId: UUID?,
    val customBrand: String?,
    val customModel: String?,
    val latitude: Double?,
    val longitude: Double?,
    val caption: String?,
    val imageBytes: ByteArray,
    val contentType: String
)
