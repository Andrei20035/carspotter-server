package features.like

import com.revio.server.features.post.IPostDAO
import com.revio.server.features.scoring.IScoringService
import org.koin.dsl.module

val likeModule = module {
    single<ILikeDAO> { LikeDAO() }
    single<ILikeService> { LikeService(get(), get<IPostDAO>(), get<IScoringService>()) }
}