package features.comment

import com.revio.server.features.post.IPostDAO
import com.revio.server.features.scoring.IScoringService
import org.koin.dsl.module

val commentModule = module {
    single<ICommentDAO> { CommentDAO() }
    single<ICommentService> { CommentService(get(), get(), get<IPostDAO>(), get<IScoringService>()) }
}
