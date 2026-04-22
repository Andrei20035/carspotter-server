package com.carspotter.features.user_car

import java.time.Instant
import java.util.UUID

data class UserCar(
    val id: UUID = UUID.randomUUID(),
    val userId: UUID,
    val carModelId: UUID,
    val imagePath: String? = null,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
)