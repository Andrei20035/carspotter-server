package com.carspotter.features.post.dto

import kotlinx.serialization.Serializable

@Serializable
data class PostEditRequest(
    val newDescription: String? = null,
)