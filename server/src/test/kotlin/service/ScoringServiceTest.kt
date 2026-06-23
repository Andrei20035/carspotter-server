package service

import com.carspotter.features.post.IPostDAO
import com.carspotter.features.post.PostSource
import com.carspotter.features.scoring.IScoringDao
import com.carspotter.features.scoring.ScoringServiceImpl
import com.carspotter.features.user.IUserDAO
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class ScoringServiceTest {

    private val userDao = mockk<IUserDAO>(relaxed = true)
    private val postDao = mockk<IPostDAO>(relaxed = true)
    private val scoringDao = mockk<IScoringDao>(relaxed = true)

    private val service = ScoringServiceImpl(userDao, postDao, scoringDao)

    private val userId = UUID.randomUUID()
    private val postId = UUID.randomUUID()
    private val otherUserId = UUID.randomUUID()

    // ---------- onPostCreated ----------

    @Test
    fun `onPostCreated CAMERA under cap awards 10 points and advances streak`() = runTest {
        coEvery { postDao.countCameraPostsOnDay(userId, any(), any()) } returns 1L

        service.onPostCreated(userId, postId, PostSource.CAMERA, Instant.now(), null)

        coVerify(exactly = 1) { scoringDao.applyCreationPoints(userId, postId, ScoringServiceImpl.CAMERA_POINTS) }
        coVerify(exactly = 1) { userDao.advanceStreak(userId, any()) }
    }

    @Test
    fun `onPostCreated CAMERA at daily cap awards 0 points but still advances streak`() = runTest {
        // priorCount = cap + 1 means priorRewarded = cap → skip points
        coEvery { postDao.countCameraPostsOnDay(userId, any(), any()) } returns (ScoringServiceImpl.DAILY_CAMERA_CAP + 1).toLong()

        service.onPostCreated(userId, postId, PostSource.CAMERA, Instant.now(), null)

        coVerify(exactly = 0) { scoringDao.applyCreationPoints(any(), any(), any()) }
        coVerify(exactly = 1) { userDao.advanceStreak(userId, any()) }
    }

    @Test
    fun `onPostCreated GALLERY is a no-op`() = runTest {
        service.onPostCreated(userId, postId, PostSource.GALLERY, Instant.now(), null)

        coVerify(exactly = 0) { scoringDao.applyCreationPoints(any(), any(), any()) }
        coVerify(exactly = 0) { userDao.advanceStreak(any(), any()) }
    }

    // ---------- onPostLiked ----------

    @Test
    fun `onPostLiked CAMERA awards LIKE_POINTS`() = runTest {
        service.onPostLiked(userId, postId, otherUserId, PostSource.CAMERA)

        coVerify(exactly = 1) { scoringDao.applyEngagementPoints(userId, postId, ScoringServiceImpl.LIKE_POINTS) }
    }

    @Test
    fun `onPostLiked GALLERY is a no-op`() = runTest {
        service.onPostLiked(userId, postId, otherUserId, PostSource.GALLERY)

        coVerify(exactly = 0) { scoringDao.applyEngagementPoints(any(), any(), any()) }
    }

    @Test
    fun `onPostLiked self-like is a no-op`() = runTest {
        service.onPostLiked(userId, postId, userId, PostSource.CAMERA)

        coVerify(exactly = 0) { scoringDao.applyEngagementPoints(any(), any(), any()) }
    }

    // ---------- onPostUnliked ----------

    @Test
    fun `onPostUnliked CAMERA removes LIKE_POINTS`() = runTest {
        service.onPostUnliked(userId, postId, otherUserId, PostSource.CAMERA)

        coVerify(exactly = 1) { scoringDao.applyEngagementPoints(userId, postId, -ScoringServiceImpl.LIKE_POINTS) }
    }

    @Test
    fun `onPostUnliked GALLERY is a no-op`() = runTest {
        service.onPostUnliked(userId, postId, otherUserId, PostSource.GALLERY)

        coVerify(exactly = 0) { scoringDao.applyEngagementPoints(any(), any(), any()) }
    }

    @Test
    fun `onPostUnliked self-unlike is a no-op`() = runTest {
        service.onPostUnliked(userId, postId, userId, PostSource.CAMERA)

        coVerify(exactly = 0) { scoringDao.applyEngagementPoints(any(), any(), any()) }
    }

    // ---------- onFirstCommentByUser ----------

    @Test
    fun `onFirstCommentByUser CAMERA awards COMMENT_POINTS`() = runTest {
        service.onFirstCommentByUser(userId, postId, otherUserId, PostSource.CAMERA)

        coVerify(exactly = 1) { scoringDao.applyEngagementPoints(userId, postId, ScoringServiceImpl.COMMENT_POINTS) }
    }

    @Test
    fun `onFirstCommentByUser GALLERY is a no-op`() = runTest {
        service.onFirstCommentByUser(userId, postId, otherUserId, PostSource.GALLERY)

        coVerify(exactly = 0) { scoringDao.applyEngagementPoints(any(), any(), any()) }
    }

    @Test
    fun `onFirstCommentByUser self-comment is a no-op`() = runTest {
        service.onFirstCommentByUser(userId, postId, userId, PostSource.CAMERA)

        coVerify(exactly = 0) { scoringDao.applyEngagementPoints(any(), any(), any()) }
    }
}
