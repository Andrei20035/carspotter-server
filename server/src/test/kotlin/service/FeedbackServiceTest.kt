package service

import com.revio.server.features.feedback.FIRST_POST_FEEDBACK_KEY
import com.revio.server.features.feedback.FeedbackService
import com.revio.server.features.feedback.FeedbackUserNotFoundException
import com.revio.server.features.feedback.IFeedbackDAO
import com.revio.server.features.feedback.PromptStatus
import com.revio.server.features.feedback.SubmitResult
import com.revio.server.features.feedback.dto.FeedbackPromptStateDTO
import com.revio.server.features.feedback.dto.PromptEvent
import com.revio.server.features.feedback.dto.SubmitFirstPostFeedbackDTO
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
}
