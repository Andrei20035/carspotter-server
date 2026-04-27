package com.carspotter.features.car_model

import com.carspotter.features.car_model.dto.CarModelOption

interface ICarModelService {
    suspend fun getAllCarBrands(): List<String>
    suspend fun getCarModelsForBrand(brand: String): List<CarModelOption>
}

class CarModelService(
    private val carModelDao: ICarModelDAO
) : ICarModelService {

    override suspend fun getAllCarBrands(): List<String> =
        carModelDao.getAllCarBrands()

    override suspend fun getCarModelsForBrand(brand: String): List<CarModelOption> {
        val normalizedBrand = normalizeCarText(brand)
        return carModelDao.getCarModelsForBrand(normalizedBrand)
    }

    private fun normalizeCarText(value: String): String {
        return value.trim().lowercase()
    }
}