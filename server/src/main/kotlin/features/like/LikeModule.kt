package com.carspotter.features.like

import org.koin.dsl.module

val likeModule = module {
    single<ILikeDAO> { LikeDAO() }
    single<ILikeRepository> { LikeRepository(get()) }
    single<ILikeService> { LikeServiceImpl(get()) }
}