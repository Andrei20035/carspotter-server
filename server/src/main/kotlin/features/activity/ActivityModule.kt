package com.carspotter.features.activity

import com.carspotter.features.leaderboard.ILeaderboardDAO
import com.carspotter.features.leaderboard.ILeaderboardSnapshotDAO
import com.carspotter.features.post.IPostDAO
import features.activity.ActivityDAO
import features.activity.IActivityDAO
import org.koin.dsl.module

val activityModule = module {
    single<IActivityDAO> { ActivityDAO() }
    single<IActivityService> {
        ActivityService(get(), get<ILeaderboardSnapshotDAO>(), get<ILeaderboardDAO>(), get<IPostDAO>(), get())
    }
}
