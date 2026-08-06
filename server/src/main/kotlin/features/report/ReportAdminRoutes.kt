package features.report

import com.revio.server.core.serialization.InstantSerializer
import com.revio.server.core.serialization.UUIDSerializer
import com.revio.server.core.util.getUuidClaim
import com.revio.server.core.util.toUuidOrNull
import com.revio.server.features.post.PostNotFoundException
import com.revio.server.features.report.ReportReason
import com.revio.server.features.report.ReportStatus
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import java.time.Instant
import java.util.UUID

@Serializable
data class ResolveReportRequest(val decision: ModerationDecision)

@Serializable
data class ReportAdminDTO(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    @Serializable(with = UUIDSerializer::class) val reporterId: UUID,
    @Serializable(with = UUIDSerializer::class) val postId: UUID,
    val reason: ReportReason,
    val status: ReportStatus,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant,
)

private fun Report.toAdminDTO() = ReportAdminDTO(
    id = id,
    reporterId = reporterId,
    postId = postId,
    reason = reason,
    status = status,
    createdAt = createdAt,
)

/**
 * Moderation queue, gated by the "admin" JWT realm (config/Security.kt). UPHOLD takes the
 * reported post down via ReportService.resolveReport -> PostService.removePostAsModerator,
 * reusing the same removal mechanics (points/challenge-reward reversal) as an author deleting
 * their own post — see PostService.removePost.
 */
fun Route.reportAdminRoutes() {
    val reportService: IReportService by application.inject()

    authenticate("admin") {
        route("/admin/reports") {
            get {
                val status = call.request.queryParameters["status"]
                    ?.let { raw ->
                        runCatching { ReportStatus.valueOf(raw.uppercase()) }
                            .getOrElse {
                                return@get call.respond(
                                    HttpStatusCode.BadRequest,
                                    mapOf("error" to "Invalid status"),
                                )
                            }
                    }
                    ?: ReportStatus.PENDING

                call.respond(HttpStatusCode.OK, reportService.listReports(status, limit = 50).map { it.toAdminDTO() })
            }

            post("/{id}/resolve") {
                val id = call.parameters["id"].toUuidOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid id"))

                val moderatorId = call.getUuidClaim("userId")
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid userId claim"))

                val req = try {
                    call.receive<ResolveReportRequest>()
                } catch (e: BadRequestException) {
                    return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid request body"))
                }

                try {
                    val report = reportService.resolveReport(id, moderatorId, req.decision)
                    call.respond(HttpStatusCode.OK, report.toAdminDTO())
                } catch (e: ReportNotFoundException) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Report not found"))
                } catch (e: PostNotFoundException) {
                    // The reported post is already gone (e.g. the author deleted it first). The
                    // report was reverted to PENDING by ReportService — surface a controlled 409
                    // rather than letting this fall through to a bare 500.
                    call.respond(HttpStatusCode.Conflict, mapOf("error" to "Reported post no longer exists"))
                }
                // Any other exception (DB failure, etc.) is unexpected and intentionally left to
                // fall through to a 500 — ReportService has already reverted the report to PENDING
                // so it stays retryable, but this isn't a condition we have a controlled response for.
            }
        }
    }
}
