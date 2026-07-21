package com.revio.server.features.account_deletion

import org.koin.dsl.module

val accountDeletionModule = module {
    single<IAccountDeletionFeedbackDAO> { AccountDeletionFeedbackDAO() }
    single<IAccountDeletionService> { AccountDeletionService(get(), get(), get(), get(), get()) }
}
