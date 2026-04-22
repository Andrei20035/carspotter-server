package com.carspotter.features.user_car

import org.koin.dsl.module

val userCarModule = module {
    single<IUserCarDAO> { UserCarDAO() }
    single<IUserCarRepository> { UserCarRepository(get()) }
    single<IUserCarService> { UserCarServiceImpl(get()) }
}