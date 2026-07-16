package com.revio.server.features.car_model.dto

import com.revio.server.core.serialization.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class CarModelOption(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val model: String,
)