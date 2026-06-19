package com.carspotter.features.post

import com.carspotter.features.car_model.ICarModelDAO
import features.comment.ICommentDAO
import features.like.ILikeDAO
import org.koin.dsl.module

val postModule = module {
    single<IPostDAO> { PostDAO() }
    single<IPostService> {
        PostServiceImpl(get(), get(), get<ICarModelDAO>(), get<ILikeDAO>(), get<ICommentDAO>())
    }
}
