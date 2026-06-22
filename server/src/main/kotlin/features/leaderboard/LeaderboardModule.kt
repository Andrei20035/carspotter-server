package com.carspotter.features.leaderboard

import org.koin.dsl.module

val leaderboardModule = module {
    single<ILeaderboardDAO> { LeaderboardDAO() }
    single<ILeaderboardService> { LeaderboardService(get(), get()) }
}
