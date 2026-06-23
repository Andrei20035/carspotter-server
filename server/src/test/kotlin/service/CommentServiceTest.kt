package service

import com.carspotter.core.storage.LocalImageStorageService
import com.carspotter.features.post.IPostDAO
import com.carspotter.features.post.PostOwnerInfo
import com.carspotter.features.post.PostSource
import com.carspotter.features.scoring.IScoringService
import features.comment.Comment
import features.comment.ICommentDAO
import features.comment.CommentForbiddenException
import features.comment.CommentNotFoundException
import features.comment.CommentService
import features.comment.CommentValidationException
import features.comment.PostNotFoundException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.sql.SQLException
import java.time.Instant
import java.util.UUID

class CommentServiceTest {

    private val commentDao = mockk<ICommentDAO>(relaxed = true)
    private val postDao = mockk<IPostDAO>(relaxed = true)
    private val scoringService = mockk<IScoringService>(relaxed = true)
    private val storage = LocalImageStorageService(Path.of("/tmp/comment-service-test-uploads"), "http://localhost:8080")

    private fun newService(
        dao: ICommentDAO = commentDao,
        pDao: IPostDAO = postDao,
        scoring: IScoringService = scoringService,
    ) = CommentService(dao, storage, pDao, scoring)

    private fun fakeComment(
        id: UUID = UUID.randomUUID(),
        userId: UUID = UUID.randomUUID(),
        postId: UUID = UUID.randomUUID(),
        text: String = "hi",
    ) = Comment(
        id = id,
        userId = userId,
        postId = postId,
        commentText = text,
        username = "alice",
        profilePicturePath = null,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    // ---------- addComment: validation ----------

    @Test
    fun `addComment trims and rejects blank text`() = runTest {
        val service = newService()
        assertThrows(CommentValidationException::class.java) {
            runBlocking { service.addComment(UUID.randomUUID(), UUID.randomUUID(), "    ") }
        }
    }

    @Test
    fun `addComment rejects empty string`() = runTest {
        val service = newService()
        assertThrows(CommentValidationException::class.java) {
            runBlocking { service.addComment(UUID.randomUUID(), UUID.randomUUID(), "") }
        }
    }

    @Test
    fun `addComment rejects text over MAX_COMMENT_LENGTH`() = runTest {
        val service = newService()
        val tooLong = "a".repeat(CommentService.MAX_COMMENT_LENGTH + 1)
        assertThrows(CommentValidationException::class.java) {
            runBlocking { service.addComment(UUID.randomUUID(), UUID.randomUUID(), tooLong) }
        }
    }

    @Test
    fun `addComment accepts boundary length exactly MAX_COMMENT_LENGTH`() = runTest {
        val dao = mockk<ICommentDAO>()
        val userId = UUID.randomUUID()
        val postId = UUID.randomUUID()
        val ownerId = UUID.randomUUID()
        val text = "a".repeat(CommentService.MAX_COMMENT_LENGTH)
        coEvery { dao.hasUserCommentedOnPost(userId, postId) } returns false
        coEvery { dao.addComment(userId, postId, text) } returns fakeComment(userId = userId, postId = postId, text = text)
        coEvery { postDao.getOwnerAndSource(postId) } returns PostOwnerInfo(ownerId, PostSource.CAMERA)

        val dto = newService(dao).addComment(userId, postId, text)

        assertEquals(text, dto.commentText)
    }

    @Test
    fun `addComment trims whitespace before passing to DAO`() = runTest {
        val dao = mockk<ICommentDAO>()
        val userId = UUID.randomUUID()
        val postId = UUID.randomUUID()
        val ownerId = UUID.randomUUID()
        val captured = slot<String>()
        coEvery { dao.hasUserCommentedOnPost(userId, postId) } returns false
        coEvery { dao.addComment(userId, postId, capture(captured)) } returns fakeComment(text = "trimmed")
        coEvery { postDao.getOwnerAndSource(postId) } returns PostOwnerInfo(ownerId, PostSource.CAMERA)

        newService(dao).addComment(userId, postId, "   trimmed   ")

        assertEquals("trimmed", captured.captured)
    }

    // ---------- addComment: error mapping ----------

    @Test
    fun `addComment converts FK violation to PostNotFoundException`() = runTest {
        val dao = mockk<ICommentDAO>()
        val postId = UUID.randomUUID()
        val ownerId = UUID.randomUUID()
        val sqlEx = SQLException("FK violation", "23503")
        coEvery { dao.hasUserCommentedOnPost(any(), postId) } returns false
        coEvery { dao.addComment(any(), any(), any()) } throws ExposedSQLException(sqlEx, emptyList(), mockk(relaxed = true))
        coEvery { postDao.getOwnerAndSource(postId) } returns PostOwnerInfo(ownerId, PostSource.CAMERA)

        assertThrows(PostNotFoundException::class.java) {
            runBlocking { newService(dao).addComment(UUID.randomUUID(), postId, "hi") }
        }
    }

    @Test
    fun `addComment rethrows non-FK SQL exceptions`() = runTest {
        val dao = mockk<ICommentDAO>()
        val postId = UUID.randomUUID()
        val ownerId = UUID.randomUUID()
        val sqlEx = SQLException("some other error", "42000")
        coEvery { dao.hasUserCommentedOnPost(any(), any()) } returns false
        coEvery { dao.addComment(any(), any(), any()) } throws ExposedSQLException(sqlEx, emptyList(), mockk(relaxed = true))
        coEvery { postDao.getOwnerAndSource(postId) } returns PostOwnerInfo(ownerId, PostSource.CAMERA)

        assertThrows(ExposedSQLException::class.java) {
            runBlocking { newService(dao).addComment(UUID.randomUUID(), postId, "hi") }
        }
    }

    @Test
    fun `addComment returns DTO from DAO result`() = runTest {
        val dao = mockk<ICommentDAO>()
        val userId = UUID.randomUUID()
        val postId = UUID.randomUUID()
        val ownerId = UUID.randomUUID()
        val expected = fakeComment(userId = userId, postId = postId, text = "ok")
        coEvery { dao.hasUserCommentedOnPost(userId, postId) } returns false
        coEvery { dao.addComment(userId, postId, "ok") } returns expected
        coEvery { postDao.getOwnerAndSource(postId) } returns PostOwnerInfo(ownerId, PostSource.CAMERA)

        val dto = newService(dao).addComment(userId, postId, "ok")

        assertEquals(expected.id, dto.id)
        assertEquals("alice", dto.username)
    }

    // ---------- addComment: scoring CAMERA vs GALLERY ----------

    @Test
    fun `addComment calls onFirstCommentByUser for first comment on CAMERA post`() = runTest {
        val userId = UUID.randomUUID()
        val ownerId = UUID.randomUUID()
        val postId = UUID.randomUUID()
        coEvery { commentDao.hasUserCommentedOnPost(userId, postId) } returns false
        coEvery { commentDao.addComment(userId, postId, "hi") } returns fakeComment(userId = userId, postId = postId)
        coEvery { postDao.getOwnerAndSource(postId) } returns PostOwnerInfo(ownerId, PostSource.CAMERA)

        newService().addComment(userId, postId, "hi")

        coVerify(exactly = 1) {
            scoringService.onFirstCommentByUser(ownerId, postId, userId, PostSource.CAMERA)
        }
    }

    @Test
    fun `addComment does not call onFirstCommentByUser for GALLERY post`() = runTest {
        val userId = UUID.randomUUID()
        val ownerId = UUID.randomUUID()
        val postId = UUID.randomUUID()
        coEvery { commentDao.hasUserCommentedOnPost(userId, postId) } returns false
        coEvery { commentDao.addComment(userId, postId, "hi") } returns fakeComment(userId = userId, postId = postId)
        coEvery { postDao.getOwnerAndSource(postId) } returns PostOwnerInfo(ownerId, PostSource.GALLERY)

        newService().addComment(userId, postId, "hi")

        coVerify(exactly = 1) {
            scoringService.onFirstCommentByUser(ownerId, postId, userId, PostSource.GALLERY)
        }
    }

    @Test
    fun `addComment does not call onFirstCommentByUser for repeat commenter`() = runTest {
        val userId = UUID.randomUUID()
        val ownerId = UUID.randomUUID()
        val postId = UUID.randomUUID()
        coEvery { commentDao.hasUserCommentedOnPost(userId, postId) } returns true
        coEvery { commentDao.addComment(userId, postId, "again") } returns fakeComment(userId = userId, postId = postId)
        coEvery { postDao.getOwnerAndSource(postId) } returns PostOwnerInfo(ownerId, PostSource.CAMERA)

        newService().addComment(userId, postId, "again")

        coVerify(exactly = 0) { scoringService.onFirstCommentByUser(any(), any(), any(), any()) }
    }

    @Test
    fun `addComment does not call onFirstCommentByUser for self-comment`() = runTest {
        val userId = UUID.randomUUID()
        val postId = UUID.randomUUID()
        coEvery { commentDao.hasUserCommentedOnPost(userId, postId) } returns false
        coEvery { commentDao.addComment(userId, postId, "hi") } returns fakeComment(userId = userId, postId = postId)
        coEvery { postDao.getOwnerAndSource(postId) } returns PostOwnerInfo(userId, PostSource.CAMERA)

        newService().addComment(userId, postId, "hi")

        coVerify(exactly = 0) { scoringService.onFirstCommentByUser(any(), any(), any(), any()) }
    }

    // ---------- deleteComment: ownership ----------

    @Test
    fun `deleteComment throws CommentNotFoundException for unknown id`() = runTest {
        val dao = mockk<ICommentDAO>()
        val commentId = UUID.randomUUID()
        coEvery { dao.getCommentById(commentId) } returns null

        val service = newService(dao)
        assertThrows(CommentNotFoundException::class.java) {
            runBlocking { service.deleteComment(commentId, UUID.randomUUID()) }
        }
        coVerify(exactly = 0) { dao.deleteComment(any()) }
    }

    @Test
    fun `deleteComment throws CommentForbiddenException for non-owner`() = runTest {
        val dao = mockk<ICommentDAO>()
        val commentId = UUID.randomUUID()
        val ownerId = UUID.randomUUID()
        val intruderId = UUID.randomUUID()
        coEvery { dao.getCommentById(commentId) } returns fakeComment(id = commentId, userId = ownerId)

        val service = newService(dao)
        assertThrows(CommentForbiddenException::class.java) {
            runBlocking { service.deleteComment(commentId, intruderId) }
        }
        coVerify(exactly = 0) { dao.deleteComment(any()) }
    }

    @Test
    fun `deleteComment proceeds for owner and calls DAO`() = runTest {
        val dao = mockk<ICommentDAO>()
        val commentId = UUID.randomUUID()
        val ownerId = UUID.randomUUID()
        coEvery { dao.getCommentById(commentId) } returns fakeComment(id = commentId, userId = ownerId)
        coEvery { dao.deleteComment(commentId) } returns 1

        val service = newService(dao)
        service.deleteComment(commentId, ownerId)

        coVerify(exactly = 1) { dao.deleteComment(commentId) }
    }

    // ---------- getCommentsForPost ----------

    @Test
    fun `getCommentsForPost maps DAO results to DTOs`() = runTest {
        val dao = mockk<ICommentDAO>()
        val postId = UUID.randomUUID()
        coEvery { dao.getCommentsForPost(postId) } returns listOf(
            fakeComment(postId = postId, text = "first"),
            fakeComment(postId = postId, text = "second"),
        )

        val dtos = newService(dao).getCommentsForPost(postId)

        assertEquals(listOf("first", "second"), dtos.map { it.commentText })
        assertEquals(listOf("alice", "alice"), dtos.map { it.username })
    }

    @Test
    fun `getCommentsForPost returns empty list when DAO returns empty`() = runTest {
        val dao = mockk<ICommentDAO>()
        coEvery { dao.getCommentsForPost(any()) } returns emptyList()

        val dtos = newService(dao).getCommentsForPost(UUID.randomUUID())

        assertEquals(0, dtos.size)
    }
}
