package features.comment

import org.koin.dsl.module

val commentModule = module {
    single<ICommentDAO> { CommentDAO() }
    single<ICommentService> { CommentService(get()) }
}