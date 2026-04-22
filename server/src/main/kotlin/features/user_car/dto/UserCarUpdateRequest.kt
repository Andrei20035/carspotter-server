package com.carspotter.features.user_car.dto

import com.carspotter.core.serialization.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class UserCarUpdateRequest(
    @Serializable(with = UUIDSerializer::class)
    val carModelId: UUID,
    val imagePath: String? = null,
)