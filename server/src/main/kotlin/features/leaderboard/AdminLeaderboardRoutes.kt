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
    cronSecretProvider: () -> String? = { System.getenv("CRON_SECRET") },
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

        // Dedicated endpoint for the external (GitHub Actions) daily cron. Distinct secret from
        // the human-operated /snapshot above, and always targets "today" in the configured zone.
        // Idempotent: snapshotAllRanks deletes-then-reinserts for the date, so repeated calls
        // (retries, at-least-once schedulers) never duplicate rows.
        post("/snapshot/today") {
            val secret = call.request.headers["X-Cron-Secret"]
            val expectedSecret = cronSecretProvider()
            if (expectedSecret.isNullOrBlank() || secret != expectedSecret) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or missing cron secret"))
                return@post
            }

            val snapshotDate = Instant.now().atZone(snapshotZone).toLocalDate()
            val rowsWritten = snapshotDao.snapshotAllRanks(snapshotDate)
            call.respond(HttpStatusCode.OK, SnapshotResultDTO(snapshotDate.toString(), rowsWritten))
        }
    }
}
