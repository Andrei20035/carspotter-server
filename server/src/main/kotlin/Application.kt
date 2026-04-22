package com.carspotter

import com.carspotter.config.configureDatabases
import com.carspotter.config.configureHTTP
import com.carspotter.config.configureMonitoring
import com.carspotter.config.configureRouting
import com.carspotter.config.configureSecurity
import com.carspotter.config.configureSerialization
import com.carspotter.config.configureSockets
import com.carspotter.core.di.appModule
import com.carspotter.features.auth.authModule
import com.carspotter.features.car_model.carModelModule
import com.carspotter.features.comment.commentModule
import com.carspotter.features.friend.friendModule
import com.carspotter.features.friend_request.friendRequestModule
import com.carspotter.features.like.likeModule
import com.carspotter.features.post.postModule
import com.carspotter.features.user.userModule
import com.carspotter.features.user_car.userCarModule
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.openapi.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.routing.*
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    install(Koin) {
        slf4jLogger()
        modules(appModule,
            authModule,
            userModule,
            commentModule,
            postModule,
            carModelModule,
            friendModule,
            friendRequestModule,
            likeModule,
            userCarModule
        )
    }

    install(RoutingRoot)

    configureSockets()
    configureSecurity()
    configureHTTP()
    configureSerialization()
    configureDatabases()
    configureMonitoring()
    configureSwagger()
    configureRouting()
}

fun Application.configureSwagger() {
    routing {
        swaggerUI(path = "swagger", swaggerFile = "openapi/documentation.yaml")
        openAPI(path = "openapi", swaggerFile = "openapi/documentation.yaml")
    }
}
