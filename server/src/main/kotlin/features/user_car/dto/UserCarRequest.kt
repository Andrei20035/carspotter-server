package com.carspotter.features.user_car.dto

import com.carspotter.core.serialization.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class UserCarRequest(
    @Serializable(with = UUIDSerializer::class)
    val carModelId: UUID? = null,
    val customBrand: String? = null,
    val customModel: String? = null,
)
