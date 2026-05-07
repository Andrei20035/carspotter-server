package com.carspotter.features.car_model

import com.carspotter.features.car_model.dto.CarModelOption
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

interface ICarModelDAO {
    suspend fun getAllCarBrands(): List<String>
    suspend fun getCarModelsForBrand(brand: String): List<CarModelOption>
    suspend fun exists(carModelId: java.util.UUID): Boolean
}

class CarModelDAO : ICarModelDAO {
    override suspend fun getAllCarBrands(): List<String> = transaction {
        CarModelTable
            .select(CarModelTable.brand)
            .withDistinct()
            .orderBy(CarModelTable.brand to SortOrder.ASC)
            .map { it[CarModelTable.brand] }
    }

    override suspend fun getCarModelsForBrand(brand: String): List<CarModelOption> = transaction {
        CarModelTable
            .select(CarModelTable.id, CarModelTable.model)
            .where { CarModelTable.brand.lowerCase() eq brand }
            .orderBy(CarModelTable.model to SortOrder.ASC)
            .map {
                CarModelOption(
                    id = it[CarModelTable.id].value,
                    model = it[CarModelTable.model],
                )
            }
    }

    override suspend fun exists(carModelId: java.util.UUID): Boolean = transaction {
        CarModelTable
            .select(CarModelTable.id)
            .where { CarModelTable.id eq carModelId }
            .limit(1)
            .any()
    }
}
