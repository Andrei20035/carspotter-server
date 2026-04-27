package com.carspotter.features.car_model.dto

import com.carspotter.core.serialization.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class CarModelOption(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val model: String,
)