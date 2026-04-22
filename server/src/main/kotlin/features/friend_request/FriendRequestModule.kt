package com.carspotter.features.friend_request

import org.koin.dsl.module

val friendRequestModule = module {
    single<IFriendRequestDAO> { FriendRequestDAO() }
    single<IFriendRequestRepository> { FriendRequestRepository(get()) }
    single<IFriendRequestService> { FriendRequestServiceImpl(get()) }
}