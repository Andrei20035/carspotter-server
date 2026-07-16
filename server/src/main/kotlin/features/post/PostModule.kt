package com.revio.server.features.post

import com.revio.server.features.car_model.ICarModelDAO
import com.revio.server.features.scoring.IScoringDao
import com.revio.server.features.scoring.IScoringService
import features.comment.ICommentDAO
import features.like.ILikeDAO
import org.koin.dsl.module

val postModule = module {
    single<IPostDAO> { PostDAO() }
    single<IPostService> {
        PostServiceImpl(get(), get(), get<ICarModelDAO>(), get<ILikeDAO>(), get<ICommentDAO>(), get<IScoringService>(), get<IScoringDao>())
    }
}
