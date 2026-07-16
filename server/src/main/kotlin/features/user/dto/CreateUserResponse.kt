package com.revio.server.features.user.dto

import com.revio.server.core.serialization.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class CreateUserResponse(
    val accessToken: String,
    val refreshToken: String,
    @Serializable(with = UUIDSerializer::class)
    val userId: UUID
)
