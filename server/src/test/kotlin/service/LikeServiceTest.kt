package service

import com.carspotter.features.post.IPostDAO
import com.carspotter.features.post.PostOwnerInfo
import com.carspotter.features.post.PostSource
import com.carspotter.features.scoring.IScoringService
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

    private val likeDao = mockk<ILikeDAO>(relaxed = true)
    private val postDao = mockk<IPostDAO>(relaxed = true)
    private val scoringService = mockk<IScoringService>(relaxed = true)

    private fun newService(
        dao: ILikeDAO = likeDao,
        pDao: IPostDAO = postDao,
        scoring: IScoringService = scoringService,
    ) = LikeService(dao, pDao, scoring)

    private fun stubCameraPost(postId: UUID, ownerId: UUID) {
        coEvery { postDao.getOwnerAndSource(postId) } returns PostOwnerInfo(ownerId, PostSource.CAMERA)
    }

    private fun stubGalleryPost(postId: UUID, ownerId: UUID) {
        coEvery { postDao.getOwnerAndSource(postId) } returns PostOwnerInfo(ownerId, PostSource.GALLERY)
    }

    // ---------- toggleLike: like path ----------

    @Test
    fun `toggleLike creates like when user has not liked and returns liked=true`() = runTest {
        val dao = mockk<ILikeDAO>()
        val userId = UUID.randomUUID()
        val postId = UUID.randomUUID()
        val ownerId = UUID.randomUUID()
        coEvery { dao.hasUserLikedPost(userId, postId) } returns false
        coEvery { dao.likePost(userId, postId) } returns Unit
        coEvery { dao.getLikeCount(postId) } returns 1L
        coEvery { postDao.getOwnerAndSource(postId) } returns PostOwnerInfo(ownerId, PostSource.CAMERA)

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
        val ownerId = UUID.randomUUID()
        coEvery { dao.hasUserLikedPost(userId, postId) } returns true
        coEvery { dao.unlikePost(userId, postId) } returns 1
        coEvery { dao.getLikeCount(postId) } returns 0L
        coEvery { postDao.getOwnerAndSource(postId) } returns PostOwnerInfo(ownerId, PostSource.CAMERA)

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
        val ownerId = UUID.randomUUID()
        coEvery { dao.hasUserLikedPost(userId, postId) } returns false
        coEvery { dao.likePost(userId, postId) } returns Unit
        coEvery { dao.getLikeCount(postId) } returns 42L
        coEvery { postDao.getOwnerAndSource(postId) } returns PostOwnerInfo(ownerId, PostSource.CAMERA)

        val result = newService(dao).toggleLike(userId, postId)

        assertEquals(42L, result.count)
    }

    // ---------- toggleLike: error mapping ----------

    @Test
    fun `toggleLike throws LikePostNotFoundException when post not found`() {
        val postId = UUID.randomUUID()
        coEvery { likeDao.hasUserLikedPost(any(), postId) } returns false
        coEvery { postDao.getOwnerAndSource(postId) } returns null

        assertThrows(LikePostNotFoundException::class.java) {
            runBlocking { newService().toggleLike(UUID.randomUUID(), postId) }
        }
    }

    @Test
    fun `toggleLike maps FK violation (23503) to LikePostNotFoundException`() {
        val dao = mockk<ILikeDAO>()
        val postId = UUID.randomUUID()
        val ownerId = UUID.randomUUID()
        coEvery { dao.hasUserLikedPost(any(), postId) } returns false
        coEvery { postDao.getOwnerAndSource(postId) } returns PostOwnerInfo(ownerId, PostSource.CAMERA)
        coEvery { dao.likePost(any(), postId) } throws
                ExposedSQLException(SQLException("FK violation", "23503"), emptyList(), mockk(relaxed = true))

        assertThrows(LikePostNotFoundException::class.java) {
            runBlocking { newService(dao).toggleLike(UUID.randomUUID(), postId) }
        }
    }

    @Test
    fun `toggleLike rethrows non-FK ExposedSQLException`() {
        val dao = mockk<ILikeDAO>()
        val postId = UUID.randomUUID()
        val ownerId = UUID.randomUUID()
        coEvery { dao.hasUserLikedPost(any(), any()) } returns false
        coEvery { postDao.getOwnerAndSource(postId) } returns PostOwnerInfo(ownerId, PostSource.CAMERA)
        coEvery { dao.likePost(any(), any()) } throws
                ExposedSQLException(SQLException("other error", "42000"), emptyList(), mockk(relaxed = true))

        assertThrows(ExposedSQLException::class.java) {
            runBlocking { newService(dao).toggleLike(UUID.randomUUID(), postId) }
        }
    }

    @Test
    fun `toggleLike does not call likePost when already liked`() = runTest {
        val dao = mockk<ILikeDAO>(relaxed = true)
        val userId = UUID.randomUUID()
        val postId = UUID.randomUUID()
        val ownerId = UUID.randomUUID()
        coEvery { dao.hasUserLikedPost(userId, postId) } returns true
        coEvery { dao.getLikeCount(postId) } returns 5L
        coEvery { postDao.getOwnerAndSource(postId) } returns PostOwnerInfo(ownerId, PostSource.CAMERA)

        newService(dao).toggleLike(userId, postId)

        coVerify(exactly = 0) { dao.likePost(any(), any()) }
    }

    // ---------- scoring: CAMERA vs GALLERY ----------

    @Test
    fun `toggleLike calls onPostLiked for CAMERA post`() = runTest {
        val userId = UUID.randomUUID()
        val ownerId = UUID.randomUUID()
        val postId = UUID.randomUUID()
        coEvery { likeDao.hasUserLikedPost(userId, postId) } returns false
        coEvery { likeDao.getLikeCount(postId) } returns 1L
        stubCameraPost(postId, ownerId)

        newService().toggleLike(userId, postId)

        coVerify(exactly = 1) {
            scoringService.onPostLiked(ownerId, postId, userId, PostSource.CAMERA)
        }
    }

    @Test
    fun `toggleLike does not call onPostLiked for GALLERY post`() = runTest {
        val userId = UUID.randomUUID()
        val ownerId = UUID.randomUUID()
        val postId = UUID.randomUUID()
        coEvery { likeDao.hasUserLikedPost(userId, postId) } returns false
        coEvery { likeDao.getLikeCount(postId) } returns 1L
        stubGalleryPost(postId, ownerId)

        newService().toggleLike(userId, postId)

        coVerify(exactly = 1) {
            scoringService.onPostLiked(ownerId, postId, userId, PostSource.GALLERY)
        }
    }

    @Test
    fun `toggleLike calls onPostUnliked for CAMERA post`() = runTest {
        val userId = UUID.randomUUID()
        val ownerId = UUID.randomUUID()
        val postId = UUID.randomUUID()
        coEvery { likeDao.hasUserLikedPost(userId, postId) } returns true
        coEvery { likeDao.getLikeCount(postId) } returns 0L
        stubCameraPost(postId, ownerId)

        newService().toggleLike(userId, postId)

        coVerify(exactly = 1) {
            scoringService.onPostUnliked(ownerId, postId, userId, PostSource.CAMERA)
        }
    }

    @Test
    fun `toggleLike calls onPostUnliked for GALLERY post`() = runTest {
        val userId = UUID.randomUUID()
        val ownerId = UUID.randomUUID()
        val postId = UUID.randomUUID()
        coEvery { likeDao.hasUserLikedPost(userId, postId) } returns true
        coEvery { likeDao.getLikeCount(postId) } returns 0L
        stubGalleryPost(postId, ownerId)

        newService().toggleLike(userId, postId)

        coVerify(exactly = 1) {
            scoringService.onPostUnliked(ownerId, postId, userId, PostSource.GALLERY)
        }
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
}
