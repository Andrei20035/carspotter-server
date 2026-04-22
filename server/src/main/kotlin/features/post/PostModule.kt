package com.carspotter.features.post

import com.carspotter.features.friend.IFriendDAO
import org.koin.dsl.module

val postModule = module {
    single<IPostDAO> { PostDAO() }
    single<IPostRepository> { PostRepository(get(), get<IFriendDAO>()) }
    single<IPostService> { PostServiceImpl(get(), get()) }
}