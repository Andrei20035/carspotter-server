package service

import com.revio.server.features.feedback.FIRST_POST_FEEDBACK_KEY
import com.revio.server.features.feedback.FeedbackCategory
import com.revio.server.features.feedback.FeedbackService
import com.revio.server.features.feedback.FeedbackSource
import com.revio.server.features.feedback.FeedbackUserNotFoundException
import com.revio.server.features.feedback.ConfusionReason
import com.revio.server.features.feedback.IFeedbackDAO
import com.revio.server.features.feedback.PromptStatus
import com.revio.server.features.feedback.SubmitResult
import com.revio.server.features.feedback.dto.FeedbackPromptStateDTO
import com.revio.server.features.feedback.dto.PromptEvent
import com.revio.server.features.feedback.dto.SubmitFirstPostFeedbackDTO
import com.revio.server.features.feedback.dto.SubmitUserFeedbackDTO
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

class FeedbackServiceTest {

    private fun newService(dao: IFeedbackDAO = mockk(relaxed = true)) = FeedbackService(dao)

    private fun dto(rating: Int = 5, comment: String? = null) = SubmitFirstPostFeedbackDTO(
        rating = rating,
        comment = comment,
    )

    @Test
    fun `submit returns CREATED when DAO inserts successfully`() = runTest {
        val dao = mockk<IFeedbackDAO>(relaxed = true)
        val userId = UUID.randomUUID()

        val result = newService(dao).submit(userId, dto())

        assertEquals(SubmitResult.CREATED, result)
        coVerify(exactly = 1) { dao.submit(userId, any()) }
        coVerify(exactly = 1) { dao.markSubmitted(userId, FIRST_POST_FEEDBACK_KEY) }
    }

    @Test
    fun `submit maps unique violation (23505) to ALREADY_SUBMITTED`() = runTest {
        val dao = mockk<IFeedbackDAO>()
        coEvery { dao.submit(any(), any()) } throws
            ExposedSQLException(SQLException("dup", "23505"), emptyList(), mockk(relaxed = true))

        val result = newService(dao).submit(UUID.randomUUID(), dto())

        assertEquals(SubmitResult.ALREADY_SUBMITTED, result)
    }

    @Test
    fun `submit maps FK violation (23503) to FeedbackUserNotFoundException`() {
        val dao = mockk<IFeedbackDAO>()
        coEvery { dao.submit(any(), any()) } throws
            ExposedSQLException(SQLException("fk", "23503"), emptyList(), mockk(relaxed = true))

        assertThrows(FeedbackUserNotFoundException::class.java) {
            runBlocking {
                newService(dao).submit(UUID.randomUUID(), dto())
            }
        }
    }

    @Test
    fun `submit rethrows unrelated ExposedSQLException`() {
        val dao = mockk<IFeedbackDAO>()
        coEvery { dao.submit(any(), any()) } throws
            ExposedSQLException(SQLException("other", "42000"), emptyList(), mockk(relaxed = true))

        assertThrows(ExposedSQLException::class.java) {
            runBlocking {
                newService(dao).submit(UUID.randomUUID(), dto())
            }
        }
    }

    @Test
    fun `submit throws IllegalArgumentException for rating below 1`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { newService().submit(UUID.randomUUID(), dto(rating = 0)) }
        }
    }

    @Test
    fun `submit throws IllegalArgumentException for rating above 5`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { newService().submit(UUID.randomUUID(), dto(rating = 6)) }
        }
    }

    @Test
    fun `submit truncates comment longer than 1000 characters`() = runTest {
        val dao = mockk<IFeedbackDAO>(relaxed = true)
        val userId = UUID.randomUUID()
        val longComment = "a".repeat(1500)

        newService(dao).submit(userId, dto(comment = longComment))

        coVerify(exactly = 1) {
            dao.submit(userId, match { it.comment?.length == 1000 })
        }
    }

    @Test
    fun `getPromptState returns ELIGIBLE default when DAO has no row`() = runTest {
        val dao = mockk<IFeedbackDAO>()
        coEvery { dao.getPromptState(any(), any()) } returns null

        val state = newService(dao).getPromptState(UUID.randomUUID(), FIRST_POST_FEEDBACK_KEY)

        assertEquals(PromptStatus.ELIGIBLE, state.status)
        assertEquals(0, state.shownCount)
    }

    @Test
    fun `recordPromptEvent SHOWN increments shownCount up to a maximum of 2`() = runTest {
        val dao = mockk<IFeedbackDAO>(relaxed = true)
        val userId = UUID.randomUUID()
        coEvery { dao.getPromptState(userId, FIRST_POST_FEEDBACK_KEY) } returns
            FeedbackPromptStateDTO(FIRST_POST_FEEDBACK_KEY, PromptStatus.DISMISSED_ONCE, shownCount = 2, lastShownAt = null)

        newService(dao).recordPromptEvent(userId, FIRST_POST_FEEDBACK_KEY, PromptEvent.SHOWN)

        coVerify(exactly = 1) {
            dao.upsertPromptState(userId, FIRST_POST_FEEDBACK_KEY, PromptStatus.DISMISSED_ONCE, 2, any())
        }
    }

    @Test
    fun `recordPromptEvent DISMISSED moves ELIGIBLE to DISMISSED_ONCE then to DISMISSED_TWICE`() = runTest {
        val dao = mockk<IFeedbackDAO>(relaxed = true)
        val userId = UUID.randomUUID()
        coEvery { dao.getPromptState(userId, FIRST_POST_FEEDBACK_KEY) } returns
            FeedbackPromptStateDTO(FIRST_POST_FEEDBACK_KEY, PromptStatus.ELIGIBLE, shownCount = 1, lastShownAt = null)

        newService(dao).recordPromptEvent(userId, FIRST_POST_FEEDBACK_KEY, PromptEvent.DISMISSED)

        coVerify(exactly = 1) {
            dao.upsertPromptState(userId, FIRST_POST_FEEDBACK_KEY, PromptStatus.DISMISSED_ONCE, 1, null)
        }
    }

    @Test
    fun `recordPromptEvent does nothing when status is already SUBMITTED`() = runTest {
        val dao = mockk<IFeedbackDAO>(relaxed = true)
        val userId = UUID.randomUUID()
        coEvery { dao.getPromptState(userId, FIRST_POST_FEEDBACK_KEY) } returns
            FeedbackPromptStateDTO(FIRST_POST_FEEDBACK_KEY, PromptStatus.SUBMITTED, shownCount = 1, lastShownAt = null)

        newService(dao).recordPromptEvent(userId, FIRST_POST_FEEDBACK_KEY, PromptEvent.SHOWN)

        coVerify(exactly = 0) { dao.upsertPromptState(any(), any(), any(), any(), any()) }
    }

    private fun userFeedbackDto(
        category: FeedbackCategory = FeedbackCategory.GENERAL,
        message: String? = "hello",
        rating: Int? = null,
        quickReason: ConfusionReason? = null,
        includeDiagnostics: Boolean = false,
        appVersion: String? = null,
    ) = SubmitUserFeedbackDTO(
        category = category,
        message = message,
        rating = rating,
        quickReason = quickReason,
        source = FeedbackSource.SETTINGS_FEEDBACK,
        includeDiagnostics = includeDiagnostics,
        appVersion = appVersion,
        clientFeedbackId = UUID.randomUUID(),
    )

    @Test
    fun `submitUserFeedback throws IllegalArgumentException for NOT_WORKING with blank message`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                newService().submitUserFeedback(
                    UUID.randomUUID(),
                    userFeedbackDto(category = FeedbackCategory.NOT_WORKING, message = "  "),
                )
            }
        }
    }

    @Test
    fun `submitUserFeedback succeeds for GENERAL with only rating and reason`() = runTest {
        val dao = mockk<IFeedbackDAO>(relaxed = true)
        val userId = UUID.randomUUID()

        val result = newService(dao).submitUserFeedback(
            userId,
            userFeedbackDto(category = FeedbackCategory.GENERAL, message = null, rating = 4, quickReason = ConfusionReason.OTHER),
        )

        assertEquals(SubmitResult.CREATED, result)
        coVerify(exactly = 1) { dao.insertUserFeedback(userId, any()) }
    }

    @Test
    fun `submitUserFeedback rejects rating outside 1 to 5`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { newService().submitUserFeedback(UUID.randomUUID(), userFeedbackDto(rating = 9)) }
        }
    }

    @Test
    fun `submitUserFeedback forces diagnostic fields to null when includeDiagnostics is false`() = runTest {
        val dao = mockk<IFeedbackDAO>(relaxed = true)
        val userId = UUID.randomUUID()

        newService(dao).submitUserFeedback(
            userId,
            userFeedbackDto(includeDiagnostics = false, appVersion = "1.0"),
        )

        coVerify(exactly = 1) {
            dao.insertUserFeedback(userId, match { it.appVersion == null })
        }
    }

    @Test
    fun `submitUserFeedback truncates message longer than 4000 characters`() = runTest {
        val dao = mockk<IFeedbackDAO>(relaxed = true)
        val userId = UUID.randomUUID()
        val longMessage = "a".repeat(10000)

        newService(dao).submitUserFeedback(userId, userFeedbackDto(message = longMessage))

        coVerify(exactly = 1) {
            dao.insertUserFeedback(userId, match { it.message?.length == 4000 })
        }
    }

    @Test
    fun `submitUserFeedback maps unique violation (23505) to ALREADY_SUBMITTED`() = runTest {
        val dao = mockk<IFeedbackDAO>()
        coEvery { dao.insertUserFeedback(any(), any()) } throws
            ExposedSQLException(SQLException("dup", "23505"), emptyList(), mockk(relaxed = true))

        val result = newService(dao).submitUserFeedback(UUID.randomUUID(), userFeedbackDto())

        assertEquals(SubmitResult.ALREADY_SUBMITTED, result)
    }

    @Test
    fun `submitUserFeedback maps FK violation (23503) to FeedbackUserNotFoundException`() {
        val dao = mockk<IFeedbackDAO>()
        coEvery { dao.insertUserFeedback(any(), any()) } throws
            ExposedSQLException(SQLException("fk", "23503"), emptyList(), mockk(relaxed = true))

        assertThrows(FeedbackUserNotFoundException::class.java) {
            runBlocking {
                newService(dao).submitUserFeedback(UUID.randomUUID(), userFeedbackDto())
            }
        }
    }
}
