package com.carspotter.features.leaderboard

import com.carspotter.core.util.resolveZone
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject
import java.time.Instant

@Serializable
data class SnapshotResultDTO(val snapshotDate: String, val rowsWritten: Int)

fun Route.adminLeaderboardRoutes(
    adminTokenProvider: () -> String? = { System.getenv("ADMIN_SNAPSHOT_TOKEN") },
    snapshotZoneId: String? = System.getenv("LEADERBOARD_SNAPSHOT_ZONE"),
) {
    val snapshotDao: ILeaderboardSnapshotDAO by application.inject()
    val snapshotZone = resolveZone(snapshotZoneId)

    route("/admin/leaderboard") {
        post("/snapshot") {
            val token = call.request.headers["X-Admin-Token"]
            val expectedToken = adminTokenProvider()
            if (expectedToken.isNullOrBlank() || token != expectedToken) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or missing admin token"))
                return@post
            }

            val snapshotDate = Instant.now().atZone(snapshotZone).toLocalDate()
            val rowsWritten = snapshotDao.snapshotAllRanks(snapshotDate)
            call.respond(HttpStatusCode.OK, SnapshotResultDTO(snapshotDate.toString(), rowsWritten))
        }
    }
}
