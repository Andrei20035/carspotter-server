package features.report

import com.carspotter.features.report.ReportReason
import org.jetbrains.exposed.exceptions.ExposedSQLException
import java.util.UUID

class ReportPostNotFoundException(postId: UUID) : RuntimeException("Post $postId not found")

/** Outcome of submitting a report. */
enum class ReportResult {
    /** A new report row was created. */
    CREATED,

    /** The user had already filed this exact report — treated as success (idempotent). */
    ALREADY_REPORTED,
}

interface IReportService {
    suspend fun submitReport(reporterId: UUID, postId: UUID, reason: ReportReason): ReportResult
}

class ReportService(
    private val reportDao: IReportDAO,
) : IReportService {

    override suspend fun submitReport(reporterId: UUID, postId: UUID, reason: ReportReason): ReportResult {
        return try {
            reportDao.createReport(reporterId, postId, reason)
            ReportResult.CREATED
        } catch (e: ExposedSQLException) {
            when (e.sqlState) {
                // 23503 = FK violation → the post doesn't exist.
                "23503" -> throw ReportPostNotFoundException(postId)
                // 23505 = unique violation → already reported this post for this reason.
                "23505" -> ReportResult.ALREADY_REPORTED
                else -> throw e
            }
        }
    }
}
