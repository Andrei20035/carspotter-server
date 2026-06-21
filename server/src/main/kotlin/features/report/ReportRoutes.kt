package features.report

import com.carspotter.core.util.getUuidClaim
import com.carspotter.core.util.toUuidOrNull
import com.carspotter.features.report.dto.ReportRequestDTO
import io.ktor.server.plugins.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.reportRoutes() {
    val reportService: IReportService by application.inject()

    route("/posts/{postId}/reports") {
        authenticate("jwt") {
            post {
                val postId = call.parameters["postId"].toUuidOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid postId"))

                val reporterId = call.getUuidClaim("userId")
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid userId claim"))

                val req = try {
                    call.receive<ReportRequestDTO>()
                } catch (e: BadRequestException) {
                    return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid report reason"))
                }

                try {
                    when (reportService.submitReport(reporterId, postId, req.reason)) {
                        ReportResult.CREATED ->
                            call.respond(HttpStatusCode.Created, mapOf("status" to "reported"))
                        ReportResult.ALREADY_REPORTED ->
                            call.respond(HttpStatusCode.OK, mapOf("status" to "already_reported"))
                    }
                } catch (e: ReportPostNotFoundException) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Post not found"))
                }
            }
        }
    }
}
