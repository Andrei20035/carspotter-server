package com.carspotter.features.car_model

import com.carspotter.core.serialization.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class CarModel(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID = UUID.randomUUID(),
    val brand: String,
    val model: String,
    val startYear: Int,
    val endYear: Int,
)