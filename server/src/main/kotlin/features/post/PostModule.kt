package com.carspotter.features.post

import com.carspotter.features.car_model.ICarModelDAO
import org.koin.dsl.module

val postModule = module {
    single<IPostDAO> { PostDAO() }
    single<IPostService> { PostServiceImpl(get(), get(), get<ICarModelDAO>()) }
}
