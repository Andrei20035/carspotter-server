package service

import com.carspotter.features.report.ReportReason
import features.report.IReportDAO
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
import java.util.UUID

class ReportServiceTest {

    private fun newService(dao: IReportDAO = mockk(relaxed = true)) = ReportService(dao)

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
}
