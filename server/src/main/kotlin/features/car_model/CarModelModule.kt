package com.carspotter.features.car_model

import org.koin.dsl.module

val carModelModule = module {
    single<ICarModelDAO> { CarModelDAO() }
    single<ICarModelRepository> { CarModelRepository(get()) }
    single<ICarModelService> { CarModelService(get()) }
}