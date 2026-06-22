package com.carspotter.features.leaderboard

import com.carspotter.core.util.getUuidClaim
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import io.ktor.server.routing.get
import org.koin.ktor.ext.inject

private const val DEFAULT_LIMIT = 50
private const val MAX_LIMIT = 200

fun Route.leaderboardRoutes() {
    val leaderboardService: ILeaderboardService by application.inject()

    authenticate("jwt") {
        get("/leaderboard") {
            val currentUserId = call.getUuidClaim("userId")
                ?: return@get call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or missing userId"))

            val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: DEFAULT_LIMIT)
                .coerceIn(1, MAX_LIMIT)

            call.respond(HttpStatusCode.OK, leaderboardService.getLeaderboard(currentUserId, limit))
        }
    }
}
