package com.revio.server.features.user_car.dto

import com.revio.server.features.user_car.UserCar
import com.revio.server.core.serialization.InstantSerializer
import com.revio.server.core.serialization.UUIDSerializer
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

@Serializable
data class UserCarDTO(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    @Serializable(with = UUIDSerializer::class)
    val userId: UUID,
    @Serializable(with = UUIDSerializer::class)
    val carModelId: UUID?,
    val brand: String,
    val model: String,
    val imageUrl: String,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant,
    @Serializable(with = InstantSerializer::class)
    val updatedAt: Instant,
)

fun UserCar.toDTO(imageUrl: String) = UserCarDTO(
    id = this.id,
    userId = this.userId,
    carModelId = this.carModelId,
    brand = this.brand,
    model = this.model,
    imageUrl = imageUrl,
    createdAt = this.createdAt,
    updatedAt = this.updatedAt
)
