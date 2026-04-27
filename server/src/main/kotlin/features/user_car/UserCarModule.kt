package com.carspotter.features.user_car

import com.carspotter.core.storage.IStorageService
import com.carspotter.features.car_model.ICarModelDAO
import org.koin.dsl.module

val userCarModule = module {
    single<IUserCarDAO> { UserCarDAO() }
    single<IUserCarService> { UserCarServiceImpl(get(), get<IStorageService>(), get<ICarModelDAO>()) }
}
