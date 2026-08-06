package features.report

import com.revio.server.features.post.IPostService
import com.revio.server.features.report.ReportReason
import com.revio.server.features.report.ReportStatus
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.slf4j.LoggerFactory
import java.util.UUID

class ReportPostNotFoundException(postId: UUID) : RuntimeException("Post $postId not found")

class ReportNotFoundException(reportId: UUID) : RuntimeException("Report $reportId not found")

/** Outcome of submitting a report. */
enum class ReportResult {
    /** A new report row was created. */
    CREATED,

    /** The user had already filed this exact report — treated as success (idempotent). */
    ALREADY_REPORTED,
}

/** A moderator's decision on a PENDING report. */
@Serializable
enum class ModerationDecision {
    /** The report was correct: take the post down. */
    UPHOLD,

    /** The report was unfounded: leave the post as-is. */
    DISMISS,
}

interface IReportService {
    suspend fun submitReport(reporterId: UUID, postId: UUID, reason: ReportReason): ReportResult

    /** Reports awaiting (or already given) [status], newest first. */
    suspend fun listReports(status: ReportStatus, limit: Int): List<Report>

    /**
     * Resolves [reportId]. UPHOLD removes the reported post via [IPostService.removePostAsModerator]
     * (which reverses its points and any challenge reward — plan §4.7) and marks the report
     * REVIEWED; DISMISS marks it DISMISSED without touching the post. Idempotent: resolving an
     * already-resolved report returns its current state without reprocessing or re-attempting removal.
     */
    suspend fun resolveReport(reportId: UUID, moderatorId: UUID, decision: ModerationDecision): Report
}

class ReportService(
    private val reportDao: IReportDAO,
    private val postService: IPostService,
) : IReportService {

    companion object {
        private val logger = LoggerFactory.getLogger(ReportService::class.java)
    }

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

    override suspend fun listReports(status: ReportStatus, limit: Int): List<Report> =
        reportDao.listByStatus(status, limit)

    override suspend fun resolveReport(reportId: UUID, moderatorId: UUID, decision: ModerationDecision): Report {
        val report = reportDao.findById(reportId) ?: throw ReportNotFoundException(reportId)
        if (report.status != ReportStatus.PENDING) {
            // Already resolved — don't re-attempt a takedown or flip the decision.
            return report
        }

        val newStatus = when (decision) {
            ModerationDecision.UPHOLD -> ReportStatus.REVIEWED
            ModerationDecision.DISMISS -> ReportStatus.DISMISSED
        }
        // Persist the decision before acting on it: reports.post_id is ON DELETE CASCADE, so if
        // UPHOLD's removal below fails partway, the report still records that a decision was made
        // rather than sitting at PENDING with no trace an admin acted (plan §4.7's MVP limitation
        // is the row disappearing entirely once the post is eventually removed, not this ordering).
        reportDao.updateStatus(reportId, newStatus)

        if (decision == ModerationDecision.UPHOLD) {
            try {
                postService.removePostAsModerator(report.postId, moderatorId)
            } catch (e: Exception) {
                // The takedown failed after we'd already marked the report REVIEWED. Left as-is,
                // the report would look resolved forever while the post stays up, and a retry
                // would short-circuit on the status check above without ever reattempting removal
                // (see the plan gap this closes). Compensate by reverting to PENDING so the
                // report stays visible in the moderation queue and can be retried — then propagate
                // the original failure; callers must not see this as a successful resolution.
                runCatching { reportDao.updateStatus(reportId, ReportStatus.PENDING) }
                    .onFailure { compensationFailure ->
                        logger.error(
                            "Failed to revert report {} to PENDING after a failed UPHOLD takedown; " +
                                "report is stuck at REVIEWED with the post still live",
                            reportId,
                            compensationFailure,
                        )
                    }
                throw e
            }
        }

        return report.copy(status = newStatus)
    }
}
