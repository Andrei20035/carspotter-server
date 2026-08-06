package features.report

import com.revio.server.features.report.ReportReason
import com.revio.server.features.report.ReportStatus
import com.revio.server.features.report.ReportTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

data class Report(
    val id: UUID,
    val reporterId: UUID,
    val postId: UUID,
    val reason: ReportReason,
    val status: ReportStatus,
    val createdAt: Instant,
)

interface IReportDAO {
    /** Inserts a new report. Throws on FK violation (post missing) or unique violation (duplicate). */
    suspend fun createReport(reporterId: UUID, postId: UUID, reason: ReportReason): UUID

    suspend fun findById(reportId: UUID): Report?

    /** Reports with [status], newest first. */
    suspend fun listByStatus(status: ReportStatus, limit: Int): List<Report>

    suspend fun updateStatus(reportId: UUID, status: ReportStatus): Int
}

class ReportDAO : IReportDAO {

    override suspend fun createReport(reporterId: UUID, postId: UUID, reason: ReportReason): UUID = transaction {
        ReportTable.insert {
            it[ReportTable.reporterId] = reporterId
            it[ReportTable.postId] = postId
            it[ReportTable.reason] = reason
            it[ReportTable.status] = ReportStatus.PENDING
        } get ReportTable.id
    }.value

    override suspend fun findById(reportId: UUID): Report? = transaction {
        ReportTable
            .selectAll()
            .where { ReportTable.id eq reportId }
            .singleOrNull()
            ?.toReport()
    }

    override suspend fun listByStatus(status: ReportStatus, limit: Int): List<Report> = transaction {
        ReportTable
            .selectAll()
            .where { ReportTable.status eq status }
            .orderBy(ReportTable.createdAt to SortOrder.DESC)
            .limit(limit)
            .map { it.toReport() }
    }

    override suspend fun updateStatus(reportId: UUID, status: ReportStatus): Int = transaction {
        ReportTable.update({ ReportTable.id eq reportId }) {
            it[ReportTable.status] = status
        }
    }

    private fun ResultRow.toReport() = Report(
        id = this[ReportTable.id].value,
        reporterId = this[ReportTable.reporterId],
        postId = this[ReportTable.postId],
        reason = this[ReportTable.reason],
        status = this[ReportTable.status],
        createdAt = this[ReportTable.createdAt],
    )
}
