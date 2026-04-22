package com.carspotter.features.auth.dto

import com.carspotter.core.serialization.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class CreateUserResponse(
    val jwtToken: String,
    @Serializable(with = UUIDSerializer::class)
    val userId: UUID
)