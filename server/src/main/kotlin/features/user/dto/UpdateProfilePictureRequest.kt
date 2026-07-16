package com.revio.server.features.user.dto

import kotlinx.serialization.Serializable

@Serializable
data class UpdateProfilePictureRequest(
    val imagePath: String
)