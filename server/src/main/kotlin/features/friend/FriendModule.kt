package com.carspotter.features.friend

import org.koin.dsl.module

val friendModule = module {
    single<IFriendDAO> { FriendDAO() }
    single<IFriendRepository> { FriendRepository(get()) }
    single<IFriendService> { FriendServiceImpl(get()) }
}