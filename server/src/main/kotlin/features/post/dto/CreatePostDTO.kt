package com.carspotter.features.post.dto

import com.carspotter.features.post.PostSource
import java.util.UUID

data class CreatePostDTO(
    val authorId: UUID,
    val carModelId: UUID?,
    val customBrand: String?,
    val customModel: String?,
    val latitude: Double?,
    val longitude: Double?,
    val town: String?,
    val country: String?,
    val caption: String?,
    val imageBytes: ByteArray,
    val contentType: String,
    val source: PostSource = PostSource.GALLERY,
    val createdAtTimezone: String? = null,
)
