package com.carspotter.features.car_model

import java.util.*

interface ICarModelService {
    suspend fun getCarModelId(brand: String, model: String): UUID?
    suspend fun getAllCarBrands(): List<String>
    suspend fun getCarModelsForBrand(brand: String): List<String>
    suspend fun createCarModel(carModel: CarModel): UUID
    suspend fun getCarModelById(carModelId: UUID): CarModel?
    suspend fun getAllCarModels(): List<CarModel>
    suspend fun deleteCarModel(carModelId: UUID): Int
}

class CarModelService(
    private val carModelRepository: ICarModelRepository
): ICarModelService {
    override suspend fun getCarModelId(brand: String, model: String): UUID? {
        return carModelRepository.getCarModelId(brand, model)
    }

    override suspend fun getAllCarBrands(): List<String> {
        return carModelRepository.getAllCarBrands()
    }

    override suspend fun getCarModelsForBrand(brand: String): List<String> {
        return carModelRepository.getCarModelsForBrand(brand)
    }

    override suspend fun createCarModel(carModel: CarModel): UUID {
        return try {
            carModelRepository.createCarModel(carModel)
        } catch (e: IllegalStateException) {
            throw IllegalArgumentException( "Failed to add car model: ${carModel.brand} ${carModel.model} (${carModel.startYear} - ${carModel.endYear})", e)
        }
    }

    override suspend fun getCarModelById(carModelId: UUID): CarModel? {
        return carModelRepository.getCarModel(carModelId)
    }

    override suspend fun getAllCarModels(): List<CarModel> {
        return carModelRepository.getAllCarModels()
    }

    override suspend fun deleteCarModel(carModelId: UUID): Int {
        return carModelRepository.deleteCarModel(carModelId)
    }

}