package com.revio.server.features.post.dto

import com.revio.server.features.user.dto.SelfUserDTO
import kotlinx.serialization.Serializable

@Serializable
data class CreatePostResponse(
    val postId: String,
    val user: SelfUserDTO? = null,
)
