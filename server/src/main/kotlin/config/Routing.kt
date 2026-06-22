package com.carspotter.config

import com.carspotter.features.auth.authRoutes
import com.carspotter.features.car_model.carModelRoutes
import features.comment.commentRoutes
import com.carspotter.features.friend.friendRoutes
import com.carspotter.features.friend_request.friendRequestRoutes
import com.carspotter.features.leaderboard.leaderboardRoutes
import features.like.likeRoutes
import features.report.reportRoutes
import com.carspotter.features.post.postRoutes
import com.carspotter.features.user.userRoutes
import com.carspotter.features.user_car.userCarRoutes
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File

fun Application.configureRouting() {
    routing {
        // Static resources
        staticResources("/static", "static")
        staticFiles("/uploads", File(System.getenv("LOCAL_STORAGE_BASE_DIR") ?: "uploads"))

        // API routes
        route("/api") {
            authRoutes()
            carModelRoutes()
            commentRoutes()
            friendRequestRoutes()
            friendRoutes()
            leaderboardRoutes()
            likeRoutes()
            reportRoutes()
            postRoutes()
            userCarRoutes()
            userRoutes()
            get("/") {
                call.respondText(
                    """Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt
                        | ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco
                        | laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit 
                        | in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat
                        | cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.""".trimMargin())
            }

        }
    }
}

