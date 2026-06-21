package features.report

import com.carspotter.features.report.ReportReason
import com.carspotter.features.report.ReportStatus
import com.carspotter.features.report.ReportTable
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

interface IReportDAO {
    /** Inserts a new report. Throws on FK violation (post missing) or unique violation (duplicate). */
    suspend fun createReport(reporterId: UUID, postId: UUID, reason: ReportReason): UUID
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
}
