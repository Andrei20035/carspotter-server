package features.like

import org.koin.dsl.module

val likeModule = module {
    single<ILikeDAO> { LikeDAO() }
    single<ILikeService> { LikeService(get()) }
}