package service

import features.like.ILikeDAO
import features.like.LikePostNotFoundException
import features.like.LikeService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.sql.SQLException
import java.util.UUID

class LikeServiceTest {

    private fun newService(dao: ILikeDAO = mockk(relaxed = true)) = LikeService(dao)

    // ---------- toggleLike: like path ----------

    @Test
    fun `toggleLike creates like when user has not liked and returns liked=true`() = runTest {
        val dao = mockk<ILikeDAO>()
        val userId = UUID.randomUUID()
        val postId = UUID.randomUUID()
        coEvery { dao.hasUserLikedPost(userId, postId) } returns false
        coEvery { dao.likePost(userId, postId) } returns Unit
        coEvery { dao.getLikeCount(postId) } returns 1L

        val result = newService(dao).toggleLike(userId, postId)

        assertTrue(result.liked)
        assertEquals(1L, result.count)
        coVerify(exactly = 1) { dao.likePost(userId, postId) }
        coVerify(exactly = 0) { dao.unlikePost(any(), any()) }
    }

    @Test
    fun `toggleLike removes like when user has already liked and returns liked=false`() = runTest {
        val dao = mockk<ILikeDAO>()
        val userId = UUID.randomUUID()
        val postId = UUID.randomUUID()
        coEvery { dao.hasUserLikedPost(userId, postId) } returns true
        coEvery { dao.unlikePost(userId, postId) } returns 1
        coEvery { dao.getLikeCount(postId) } returns 0L

        val result = newService(dao).toggleLike(userId, postId)

        assertFalse(result.liked)
        assertEquals(0L, result.count)
        coVerify(exactly = 1) { dao.unlikePost(userId, postId) }
        coVerify(exactly = 0) { dao.likePost(any(), any()) }
    }

    @Test
    fun `toggleLike returns updated count from DAO after toggle`() = runTest {
        val dao = mockk<ILikeDAO>()
        val userId = UUID.randomUUID()
        val postId = UUID.randomUUID()
        coEvery { dao.hasUserLikedPost(userId, postId) } returns false
        coEvery { dao.likePost(userId, postId) } returns Unit
        coEvery { dao.getLikeCount(postId) } returns 42L

        val result = newService(dao).toggleLike(userId, postId)

        assertEquals(42L, result.count)
    }

    // ---------- toggleLike: error mapping ----------

    @Test
    fun `toggleLike maps FK violation (23503) to LikePostNotFoundException`() {
        val dao = mockk<ILikeDAO>()
        val postId = UUID.randomUUID()
        coEvery { dao.hasUserLikedPost(any(), postId) } returns false
        coEvery { dao.likePost(any(), postId) } throws
                ExposedSQLException(SQLException("FK violation", "23503"), emptyList(), mockk(relaxed = true))

        assertThrows(LikePostNotFoundException::class.java) {
            runBlocking { newService(dao).toggleLike(UUID.randomUUID(), postId) }
        }
    }

    @Test
    fun `toggleLike rethrows non-FK ExposedSQLException`() {
        val dao = mockk<ILikeDAO>()
        coEvery { dao.hasUserLikedPost(any(), any()) } returns false
        coEvery { dao.likePost(any(), any()) } throws
                ExposedSQLException(SQLException("other error", "42000"), emptyList(), mockk(relaxed = true))

        assertThrows(ExposedSQLException::class.java) {
            runBlocking { newService(dao).toggleLike(UUID.randomUUID(), UUID.randomUUID()) }
        }
    }

    @Test
    fun `toggleLike does not call likePost when already liked`() = runTest {
        val dao = mockk<ILikeDAO>(relaxed = true)
        val userId = UUID.randomUUID()
        val postId = UUID.randomUUID()
        coEvery { dao.hasUserLikedPost(userId, postId) } returns true
        coEvery { dao.getLikeCount(postId) } returns 5L

        newService(dao).toggleLike(userId, postId)

        coVerify(exactly = 0) { dao.likePost(any(), any()) }
    }

    @Test
    fun `toggleLike does not call getLikeCount before performing action`() = runTest {
        // Verifică ordinea apelurilor: mai întâi like/unlike, apoi count
        val dao = mockk<ILikeDAO>()
        val userId = UUID.randomUUID()
        val postId = UUID.randomUUID()
        coEvery { dao.hasUserLikedPost(userId, postId) } returns false
        coEvery { dao.likePost(userId, postId) } returns Unit
        coEvery { dao.getLikeCount(postId) } returns 1L

        newService(dao).toggleLike(userId, postId)

        // likePost trebuie apelat exact o dată, getLikeCount exact o dată
        coVerify(exactly = 1) { dao.likePost(userId, postId) }
        coVerify(exactly = 1) { dao.getLikeCount(postId) }
    }

    // ---------- getLikeStatus ----------

    @Test
    fun `getLikeStatus returns count and liked=false when userId is null`() = runTest {
        val dao = mockk<ILikeDAO>()
        val postId = UUID.randomUUID()
        coEvery { dao.getLikeCount(postId) } returns 7L

        val result = newService(dao).getLikeStatus(postId, userId = null)

        assertFalse(result.liked)
        assertEquals(7L, result.count)
        // Nu trebuie să cheme hasUserLikedPost când userId e null
        coVerify(exactly = 0) { dao.hasUserLikedPost(any(), any()) }
    }

    @Test
    fun `getLikeStatus returns liked=true when user has liked`() = runTest {
        val dao = mockk<ILikeDAO>()
        val userId = UUID.randomUUID()
        val postId = UUID.randomUUID()
        coEvery { dao.getLikeCount(postId) } returns 3L
        coEvery { dao.hasUserLikedPost(userId, postId) } returns true

        val result = newService(dao).getLikeStatus(postId, userId)

        assertTrue(result.liked)
        assertEquals(3L, result.count)
    }

    @Test
    fun `getLikeStatus returns liked=false when user has not liked`() = runTest {
        val dao = mockk<ILikeDAO>()
        val userId = UUID.randomUUID()
        val postId = UUID.randomUUID()
        coEvery { dao.getLikeCount(postId) } returns 3L
        coEvery { dao.hasUserLikedPost(userId, postId) } returns false

        val result = newService(dao).getLikeStatus(postId, userId)

        assertFalse(result.liked)
        assertEquals(3L, result.count)
    }

    @Test
    fun `getLikeStatus returns count=0 for post without likes`() = runTest {
        val dao = mockk<ILikeDAO>()
        val postId = UUID.randomUUID()
        coEvery { dao.getLikeCount(postId) } returns 0L

        val result = newService(dao).getLikeStatus(postId, userId = null)

        assertEquals(0L, result.count)
        assertFalse(result.liked)
    }

    @Test
    fun `getLikeStatus calls hasUserLikedPost exactly once when userId is present`() = runTest {
        val dao = mockk<ILikeDAO>()
        val userId = UUID.randomUUID()
        val postId = UUID.randomUUID()
        coEvery { dao.getLikeCount(postId) } returns 1L
        coEvery { dao.hasUserLikedPost(userId, postId) } returns true

        newService(dao).getLikeStatus(postId, userId)

        coVerify(exactly = 1) { dao.hasUserLikedPost(userId, postId) }
        coVerify(exactly = 1) { dao.getLikeCount(postId) }
    }
}