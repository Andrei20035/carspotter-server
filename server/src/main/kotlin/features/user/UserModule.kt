package com.carspotter.features.user

import org.koin.dsl.module

val userModule = module {
    single<IUserDAO> { UserDao() }
    single<IUserRepository> { UserRepository(get()) }
    single<IUserService> { UserService(get()) }
}