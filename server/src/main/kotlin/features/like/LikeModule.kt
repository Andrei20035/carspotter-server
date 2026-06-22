package features.like

import com.carspotter.features.post.IPostDAO
import com.carspotter.features.scoring.IScoringService
import org.koin.dsl.module

val likeModule = module {
    single<ILikeDAO> { LikeDAO() }
    single<ILikeService> { LikeService(get(), get<IPostDAO>(), get<IScoringService>()) }
}