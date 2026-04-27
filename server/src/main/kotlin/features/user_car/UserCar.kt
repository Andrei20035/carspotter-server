package com.carspotter.features.user_car

import java.time.Instant
import java.util.UUID

data class UserCar(
    val id: UUID = UUID.randomUUID(),
    val userId: UUID,
    val carModelId: UUID?,
    val customBrand: String?,
    val customModel: String?,
    val brand: String,
    val model: String,
    val imageKey: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)
