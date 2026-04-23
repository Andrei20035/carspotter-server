package com.carspotter.features.car_model

import org.jetbrains.exposed.dao.id.UUIDTable

object CarModelTable : UUIDTable("car_models") {
    val brand = varchar("brand", 50)
    val model = varchar("model", 50)

    init {
        uniqueIndex(brand, model)
    }

}