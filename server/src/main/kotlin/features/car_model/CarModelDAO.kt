package com.carspotter.features.car_model

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

interface ICarModelDAO {
    suspend fun getCarModelId(brand: String, model: String): UUID?
    suspend fun getAllCarBrands(): List<String>
    suspend fun getCarModelsForBrand(brand: String): List<String>
    suspend fun createCarModel(carModel: CarModel): UUID
    suspend fun getCarModel(carModelId: UUID): CarModel?
    suspend fun getAllCarModels(): List<CarModel>
    suspend fun deleteCarModel(carModelId: UUID): Int
}

class CarModelDAO : ICarModelDAO {
    override suspend fun createCarModel(carModel: CarModel): UUID {
        return transaction {
            CarModelTable
                .insertReturning(listOf(CarModelTable.id)) {
                    it[brand] = carModel.brand
                    it[model] = carModel.model
                    it[startYear] = carModel.startYear
                    it[endYear] = carModel.endYear
                }.singleOrNull()?.get(CarModelTable.id)?.value ?: throw IllegalStateException("Failed to insert car model")
        }
    }

    override suspend fun getCarModel(carModelId: UUID): CarModel? {
        return transaction {
            CarModelTable
                .selectAll()
                .where { CarModelTable.id eq carModelId }
                .mapNotNull { row ->
                    CarModel(
                        id = row[CarModelTable.id].value,
                        brand = row[CarModelTable.brand],
                        model = row[CarModelTable.model],
                        startYear = row[CarModelTable.startYear],
                        endYear = row[CarModelTable.endYear]
                    )
                }.singleOrNull()
        }
    }

    override suspend fun getCarModelId(brand: String, model: String): UUID? {
        return transaction {
            CarModelTable
                .selectAll()
                .where { (CarModelTable.brand eq brand) and (CarModelTable.model eq model) }
                .mapNotNull { it[CarModelTable.id].value }
                .singleOrNull()
        }
    }

    override suspend fun getAllCarBrands(): List<String> {
        return transaction {
            CarModelTable
                .select(CarModelTable.brand)
                .withDistinct()
                .orderBy(CarModelTable.brand to SortOrder.ASC)
                .map { it[CarModelTable.brand] }
        }
    }

    override suspend fun getCarModelsForBrand(brand: String): List<String> {
        return transaction {
            CarModelTable
                .select(CarModelTable.model)
                .orderBy(CarModelTable.model to SortOrder.ASC)
                .where { CarModelTable.brand eq brand }
                .withDistinct()
                .map { it[CarModelTable.model] }
        }
    }

    override suspend fun getAllCarModels(): List<CarModel> {
        return transaction {
            CarModelTable
                .selectAll()
                .orderBy(CarModelTable.brand to SortOrder.ASC, CarModelTable.model to SortOrder.ASC)
                .mapNotNull { row ->
                    CarModel(
                        id = row[CarModelTable.id].value,
                        brand = row[CarModelTable.brand],
                        model = row[CarModelTable.model],
                        startYear = row[CarModelTable.startYear],
                        endYear = row[CarModelTable.endYear]
                    )
                }
        }
    }

    override suspend fun deleteCarModel(carModelId: UUID): Int {
        return transaction {
            CarModelTable
                .deleteWhere { id eq carModelId }
        }
    }


}