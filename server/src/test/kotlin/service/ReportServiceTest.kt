package service

import com.revio.server.features.post.IPostService
import com.revio.server.features.post.PostNotFoundException
import com.revio.server.features.report.ReportReason
import com.revio.server.features.report.ReportStatus
import features.report.IReportDAO
import features.report.ModerationDecision
import features.report.Report
import features.report.ReportNotFoundException
import features.report.ReportPostNotFoundException
import features.report.ReportResult
import features.report.ReportService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.sql.SQLException
import java.time.Instant
import java.util.UUID

class ReportServiceTest {

    private fun newService(dao: IReportDAO = mockk(relaxed = true)) = ReportService(dao, mockk<IPostService>(relaxed = true))

    @Test
    fun `submitReport returns CREATED when DAO inserts successfully`() = runTest {
        val dao = mockk<IReportDAO>()
        val userId = UUID.randomUUID()
        val postId = UUID.randomUUID()
        coEvery { dao.createReport(userId, postId, ReportReason.INCORRECT_CAR_MODEL) } returns UUID.randomUUID()

        val result = newService(dao).submitReport(userId, postId, ReportReason.INCORRECT_CAR_MODEL)

        assertEquals(ReportResult.CREATED, result)
        coVerify(exactly = 1) { dao.createReport(userId, postId, ReportReason.INCORRECT_CAR_MODEL) }
    }

    @Test
    fun `submitReport maps unique violation (23505) to ALREADY_REPORTED`() = runTest {
        val dao = mockk<IReportDAO>()
        coEvery { dao.createReport(any(), any(), any()) } throws
            ExposedSQLException(SQLException("dup", "23505"), emptyList(), mockk(relaxed = true))

        val result = newService(dao).submitReport(
            UUID.randomUUID(), UUID.randomUUID(), ReportReason.DUPLICATE_POST
        )

        assertEquals(ReportResult.ALREADY_REPORTED, result)
    }

    @Test
    fun `submitReport maps FK violation (23503) to ReportPostNotFoundException`() {
        val dao = mockk<IReportDAO>()
        val postId = UUID.randomUUID()
        coEvery { dao.createReport(any(), postId, any()) } throws
            ExposedSQLException(SQLException("fk", "23503"), emptyList(), mockk(relaxed = true))

        assertThrows(ReportPostNotFoundException::class.java) {
            runBlocking {
                newService(dao).submitReport(UUID.randomUUID(), postId, ReportReason.INAPPROPRIATE_CONTENT)
            }
        }
    }

    @Test
    fun `submitReport rethrows unrelated ExposedSQLException`() {
        val dao = mockk<IReportDAO>()
        coEvery { dao.createReport(any(), any(), any()) } throws
            ExposedSQLException(SQLException("other", "42000"), emptyList(), mockk(relaxed = true))

        assertThrows(ExposedSQLException::class.java) {
            runBlocking {
                newService(dao).submitReport(UUID.randomUUID(), UUID.randomUUID(), ReportReason.DUPLICATE_POST)
            }
        }
    }

    @Test
    fun `submitReport passes the reason through to the DAO unchanged`() = runTest {
        val dao = mockk<IReportDAO>(relaxed = true)
        val userId = UUID.randomUUID()
        val postId = UUID.randomUUID()

        newService(dao).submitReport(userId, postId, ReportReason.INAPPROPRIATE_CONTENT)

        coVerify(exactly = 1) { dao.createReport(userId, postId, ReportReason.INAPPROPRIATE_CONTENT) }
    }

    // ---------- resolveReport ----------

    private fun pendingReport(
        reportId: UUID = UUID.randomUUID(),
        postId: UUID = UUID.randomUUID(),
    ) = Report(
        id = reportId,
        reporterId = UUID.randomUUID(),
        postId = postId,
        reason = ReportReason.INAPPROPRIATE_CONTENT,
        status = ReportStatus.PENDING,
        createdAt = Instant.now(),
    )

    @Test
    fun `resolveReport with UPHOLD removes the post and marks the report REVIEWED`() = runTest {
        val dao = mockk<IReportDAO>(relaxed = true)
        val postService = mockk<IPostService>(relaxed = true)
        val moderatorId = UUID.randomUUID()
        val report = pendingReport()
        coEvery { dao.findById(report.id) } returns report

        val result = ReportService(dao, postService).resolveReport(report.id, moderatorId, ModerationDecision.UPHOLD)

        assertEquals(ReportStatus.REVIEWED, result.status)
        coVerify(exactly = 1) { postService.removePostAsModerator(report.postId, moderatorId) }
        coVerify(exactly = 1) { dao.updateStatus(report.id, ReportStatus.REVIEWED) }
        coVerify(exactly = 0) { dao.updateStatus(report.id, ReportStatus.PENDING) }
    }

    @Test
    fun `resolveReport with UPHOLD reverts the report to PENDING and rethrows when the takedown fails`() = runTest {
        val dao = mockk<IReportDAO>(relaxed = true)
        val postService = mockk<IPostService>()
        val moderatorId = UUID.randomUUID()
        val report = pendingReport()
        coEvery { dao.findById(report.id) } returns report
        coEvery { postService.removePostAsModerator(report.postId, moderatorId) } throws
            IllegalStateException("simulated takedown failure")

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                ReportService(dao, postService).resolveReport(report.id, moderatorId, ModerationDecision.UPHOLD)
            }
        }

        // Marked REVIEWED first (so a crash mid-removal still shows a decision was made), then
        // reverted to PENDING once the takedown itself failed, in that order.
        coVerify(exactly = 1) { dao.updateStatus(report.id, ReportStatus.REVIEWED) }
        coVerify(exactly = 1) { dao.updateStatus(report.id, ReportStatus.PENDING) }
    }

    @Test
    fun `resolveReport can be retried successfully after a failed takedown`() = runTest {
        val dao = mockk<IReportDAO>()
        val postService = mockk<IPostService>()
        val moderatorId = UUID.randomUUID()
        val report = pendingReport()

        // First attempt: report is PENDING, findById reflects that, removal fails.
        coEvery { dao.findById(report.id) } returns report
        coEvery { dao.updateStatus(any(), any()) } returns 1
        coEvery { postService.removePostAsModerator(report.postId, moderatorId) } throws
            IllegalStateException("simulated takedown failure") andThen Unit

        val service = ReportService(dao, postService)
        assertThrows(IllegalStateException::class.java) {
            runBlocking { service.resolveReport(report.id, moderatorId, ModerationDecision.UPHOLD) }
        }

        // The compensation put it back at PENDING — the retry must see that, not REVIEWED, or it
        // would short-circuit on the already-resolved check instead of reattempting removal.
        coEvery { dao.findById(report.id) } returns report.copy(status = ReportStatus.PENDING)

        val retried = service.resolveReport(report.id, moderatorId, ModerationDecision.UPHOLD)

        assertEquals(ReportStatus.REVIEWED, retried.status)
        coVerify(exactly = 2) { postService.removePostAsModerator(report.postId, moderatorId) }
    }

    @Test
    fun `resolveReport with DISMISS marks the report DISMISSED without touching the post`() = runTest {
        val dao = mockk<IReportDAO>(relaxed = true)
        val postService = mockk<IPostService>()
        val moderatorId = UUID.randomUUID()
        val report = pendingReport()
        coEvery { dao.findById(report.id) } returns report

        val result = ReportService(dao, postService).resolveReport(report.id, moderatorId, ModerationDecision.DISMISS)

        assertEquals(ReportStatus.DISMISSED, result.status)
        coVerify(exactly = 1) { dao.updateStatus(report.id, ReportStatus.DISMISSED) }
        coVerify(exactly = 0) { postService.removePostAsModerator(any(), any()) }
    }

    @Test
    fun `resolveReport throws ReportNotFoundException for an unknown report`() {
        val dao = mockk<IReportDAO>()
        val reportId = UUID.randomUUID()
        coEvery { dao.findById(reportId) } returns null

        assertThrows(ReportNotFoundException::class.java) {
            runBlocking {
                ReportService(dao, mockk(relaxed = true)).resolveReport(reportId, UUID.randomUUID(), ModerationDecision.DISMISS)
            }
        }
    }

    @Test
    fun `resolveReport with UPHOLD reverts to PENDING and propagates PostNotFoundException`() = runTest {
        val dao = mockk<IReportDAO>(relaxed = true)
        val postService = mockk<IPostService>()
        val moderatorId = UUID.randomUUID()
        val report = pendingReport()
        coEvery { dao.findById(report.id) } returns report
        coEvery { postService.removePostAsModerator(report.postId, moderatorId) } throws PostNotFoundException(report.postId)

        assertThrows(PostNotFoundException::class.java) {
            runBlocking {
                ReportService(dao, postService).resolveReport(report.id, moderatorId, ModerationDecision.UPHOLD)
            }
        }

        coVerify(exactly = 1) { dao.updateStatus(report.id, ReportStatus.PENDING) }
    }
}
