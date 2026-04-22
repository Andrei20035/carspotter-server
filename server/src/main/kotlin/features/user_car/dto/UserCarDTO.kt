package com.carspotter.features.user_car.dto

import com.carspotter.features.user_car.UserCar
import com.carspotter.core.serialization.InstantSerializer
import com.carspotter.core.serialization.UUIDSerializer
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.*

@Serializable
data class UserCarDTO(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID = UUID.randomUUID(),
    @Serializable(with = UUIDSerializer::class)
    val userId: UUID,
    @Serializable(with = UUIDSerializer::class)
    val carModelId: UUID,
    val imagePath: String? = null,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant? = null,
    @Serializable(with = InstantSerializer::class)
    val updatedAt: Instant? = null
)

fun UserCar.toDTO() = UserCarDTO(
    id = this.id,
    userId = this.userId,
    carModelId = this.carModelId,
    imagePath = this.imagePath,
    createdAt = this.createdAt,
    updatedAt = this.updatedAt
)
