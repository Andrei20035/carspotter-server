package com.carspotter

import com.carspotter.config.configureDatabases
import com.carspotter.config.configureAuthStatusPages
import com.carspotter.config.configureHTTP
import com.carspotter.config.configureMonitoring
import com.carspotter.config.configureRouting
import com.carspotter.config.configureSecurity
import com.carspotter.config.configureSerialization
import com.carspotter.config.configureSockets
import com.carspotter.core.di.appModule
import com.carspotter.core.util.resolveZone
import com.carspotter.features.activity.activityModule
import com.carspotter.features.auth.authModule
import com.carspotter.features.car_model.carModelModule
import features.comment.commentModule
import com.carspotter.features.friend.friendModule
import com.carspotter.features.friend_request.friendRequestModule
import features.like.likeModule
import features.report.reportModule
import com.carspotter.features.leaderboard.ILeaderboardSnapshotDAO
import com.carspotter.features.leaderboard.leaderboardModule
import com.carspotter.features.post.postModule
import com.carspotter.features.scoring.scoringModule
import com.carspotter.features.user.userModule
import com.carspotter.features.user_car.userCarModule
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.openapi.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import org.koin.ktor.ext.getKoin
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import java.time.Instant

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    install(Koin) {
        slf4jLogger()
        modules(appModule,
            authModule,
            userModule,
            scoringModule,
            commentModule,
            postModule,
            carModelModule,
            friendModule,
            friendRequestModule,
            likeModule,
            reportModule,
            userCarModule,
            leaderboardModule,
            activityModule
        )
    }

    install(RoutingRoot)

    configureSockets()
    configureSecurity()
    configureHTTP()
    configureSerialization()
    configureAuthStatusPages()
    configureDatabases()
    configureMonitoring()
    configureSwagger()
    configureRouting()

    // Dev-only convenience: when the external cron (see /admin/leaderboard/snapshot/today)
    // hasn't run yet, this backfills today's leaderboard snapshot once at boot so
    // LEADERBOARD_UP/weeklySpotScore have something to work with locally. Off by default —
    // production always relies on the external cron, never on this.
    if (System.getenv("ENABLE_SNAPSHOT_CATCHUP_ON_STARTUP") == "true") {
        val snapshotDao = getKoin().get<ILeaderboardSnapshotDAO>()
        val snapshotZone = resolveZone(System.getenv("LEADERBOARD_SNAPSHOT_ZONE"))
        runBlocking { snapshotDao.snapshotAllRanks(Instant.now().atZone(snapshotZone).toLocalDate()) }
    }
}

fun Application.configureSwagger() {
    routing {
        swaggerUI(path = "swagger", swaggerFile = "openapi/documentation.yaml")
        openAPI(path = "openapi", swaggerFile = "openapi/documentation.yaml")
    }
}
