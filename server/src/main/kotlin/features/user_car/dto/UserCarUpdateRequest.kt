package com.revio.server.features.user_car.dto

import com.revio.server.core.serialization.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class UserCarUpdateRequest(
    @Serializable(with = UUIDSerializer::class)
    val carModelId: UUID? = null,
    val customBrand: String? = null,
    val customModel: String? = null,
)
