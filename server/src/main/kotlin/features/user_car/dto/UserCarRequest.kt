package com.carspotter.features.user_car.dto

import com.carspotter.features.user_car.UserCar
import com.carspotter.core.serialization.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class UserCarRequest(
    @Serializable(with = UUIDSerializer::class)
    val userId: UUID,
    @Serializable(with = UUIDSerializer::class)
    val carModelId: UUID,
    val imagePath: String,
)

fun UserCarRequest.toUserCar(userId: UUID) = UserCar(
    userId = userId,
    carModelId = this.carModelId,
    imagePath = this.imagePath,
)
