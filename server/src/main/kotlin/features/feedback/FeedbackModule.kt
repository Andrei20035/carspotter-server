package com.revio.server.features.feedback

import org.koin.dsl.module

val feedbackModule = module {
    single<IFeedbackDAO> { FeedbackDAO() }
    single<IFeedbackService> { FeedbackService(get()) }
}
