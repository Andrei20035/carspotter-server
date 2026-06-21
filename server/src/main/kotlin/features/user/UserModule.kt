package com.carspotter.features.user

import org.koin.dsl.module

val userModule = module {
    single<IUserDAO> { UserDao() }
    single<IUserService> { UserService(get(), get()) }
}
