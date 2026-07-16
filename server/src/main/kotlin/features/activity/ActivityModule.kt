package com.revio.server.features.activity

import com.revio.server.features.leaderboard.ILeaderboardDAO
import com.revio.server.features.leaderboard.ILeaderboardSnapshotDAO
import com.revio.server.features.post.IPostDAO
import features.activity.ActivityDAO
import features.activity.IActivityDAO
import org.koin.dsl.module

val activityModule = module {
    single<IActivityDAO> { ActivityDAO() }
    single<IActivityService> {
        ActivityService(get(), get<ILeaderboardSnapshotDAO>(), get<ILeaderboardDAO>(), get<IPostDAO>(), get())
    }
}
