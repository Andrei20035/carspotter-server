package com.revio.server.features.user_car

import com.revio.server.core.storage.IStorageService
import com.revio.server.features.car_model.ICarModelDAO
import org.koin.dsl.module

val userCarModule = module {
    single<IUserCarDAO> { UserCarDAO() }
    single<IUserCarService> { UserCarServiceImpl(get(), get<IStorageService>(), get<ICarModelDAO>()) }
}
