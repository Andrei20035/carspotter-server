package com.carspotter.features.comment

import org.koin.dsl.module

val commentModule = module {
    single<ICommentDAO> { CommentDAO() }
    single<ICommentRepository> { CommentRepository(get()) }
    single<ICommentService> { CommentService(get()) }
}