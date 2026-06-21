package testutils

import com.carspotter.features.report.ReportReason
import com.carspotter.features.report.ReportStatus
import com.carspotter.features.report.ReportTable
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

/**
 * Helpers pentru a popula / inspecta tabela reports direct în DB, ocolind service-ul.
 */
object ReportTestSeed {

    fun insertReport(
        reporterId: UUID,
        postId: UUID,
        reason: ReportReason = ReportReason.INAPPROPRIATE_CONTENT,
    ): Unit = transaction {
        ReportTable.insert {
            it[ReportTable.reporterId] = reporterId
            it[ReportTable.postId] = postId
            it[ReportTable.reason] = reason
            it[ReportTable.status] = ReportStatus.PENDING
        }
        Unit
    }

    fun reportExists(reporterId: UUID, postId: UUID, reason: ReportReason): Boolean = transaction {
        ReportTable
            .selectAll()
            .where {
                (ReportTable.reporterId eq reporterId) and
                    (ReportTable.postId eq postId) and
                    (ReportTable.reason eq reason)
            }
            .any()
    }

    fun countReports(postId: UUID): Long = transaction {
        ReportTable
            .selectAll()
            .where { ReportTable.postId eq postId }
            .count()
    }
}
