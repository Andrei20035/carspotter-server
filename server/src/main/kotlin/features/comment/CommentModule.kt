package features.comment

import com.carspotter.features.post.IPostDAO
import com.carspotter.features.scoring.IScoringService
import org.koin.dsl.module

val commentModule = module {
    single<ICommentDAO> { CommentDAO() }
    single<ICommentService> { CommentService(get(), get(), get<IPostDAO>(), get<IScoringService>()) }
}
