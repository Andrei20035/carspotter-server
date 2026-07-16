package service

import com.revio.server.features.activity.ActivityEventType
import com.revio.server.features.post.IPostDAO
import com.revio.server.features.post.PostSource
import com.revio.server.features.scoring.IScoringDao
import com.revio.server.features.scoring.ScoringServiceImpl
import com.revio.server.features.user.IUserDAO
import com.revio.server.features.user.User
import features.activity.IActivityDAO
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

class ScoringServiceTest {

    private val userDao = mockk<IUserDAO>(relaxed = true)
    private val postDao = mockk<IPostDAO>(relaxed = true)
    private val scoringDao = mockk<IScoringDao>(relaxed = true)
    private val activityDao = mockk<IActivityDAO>(relaxed = true)

    private val service = ScoringServiceImpl(userDao, postDao, scoringDao, activityDao)

    private val userId = UUID.randomUUID()
    private val postId = UUID.randomUUID()
    private val otherUserId = UUID.randomUUID()

    private fun testUser(currentStreak: Int, lastStreakDate: LocalDate?) = User(
        id = userId,
        authCredentialId = UUID.randomUUID(),
        fullName = "Alice",
        phoneNumber = null,
        birthDate = LocalDate.of(1995, 1, 1),
        username = "alice",
        country = "RO",
        currentStreak = currentStreak,
        lastStreakDate = lastStreakDate,
    )

    // ---------- onPostCreated ----------

    @Test
    fun `onPostCreated CAMERA under cap awards 10 points and advances streak`() = runTest {
        coEvery { postDao.countCameraPostsOnDay(userId, any(), any()) } returns 1L

        service.onPostCreated(userId, postId, PostSource.CAMERA, Instant.now(), null)

        coVerify(exactly = 1) { scoringDao.applyCreationPoints(userId, postId, ScoringServiceImpl.CAMERA_POINTS) }
        coVerify(exactly = 1) { userDao.advanceStreak(userId, any(), any()) }
    }

    @Test
    fun `onPostCreated CAMERA at daily cap awards 0 points but still advances streak`() = runTest {
        // priorCount = cap + 1 means priorRewarded = cap → skip points
        coEvery { postDao.countCameraPostsOnDay(userId, any(), any()) } returns (ScoringServiceImpl.DAILY_CAMERA_CAP + 1).toLong()

        service.onPostCreated(userId, postId, PostSource.CAMERA, Instant.now(), null)

        coVerify(exactly = 0) { scoringDao.applyCreationPoints(any(), any(), any()) }
        coVerify(exactly = 1) { userDao.advanceStreak(userId, any(), any()) }
    }

    @Test
    fun `onPostCreated GALLERY is a no-op`() = runTest {
        service.onPostCreated(userId, postId, PostSource.GALLERY, Instant.now(), null)

        coVerify(exactly = 0) { scoringDao.applyCreationPoints(any(), any(), any()) }
        coVerify(exactly = 0) { userDao.advanceStreak(any(), any(), any()) }
        coVerify(exactly = 0) { activityDao.recordEventIdempotent(any(), any(), any(), any()) }
    }

    // ---------- onPostCreated: STREAK activity event ----------

    @Test
    fun `onPostCreated writes a STREAK activity event when the streak actually advances`() = runTest {
        val today = LocalDate.now(ZoneOffset.UTC)
        val yesterday = today.minusDays(1)
        coEvery { postDao.countCameraPostsOnDay(userId, any(), any()) } returns 1L
        coEvery { userDao.getUserById(userId) } returnsMany listOf(
            testUser(currentStreak = 4, lastStreakDate = yesterday), // before advanceStreak
            testUser(currentStreak = 5, lastStreakDate = today),     // after advanceStreak
        )

        service.onPostCreated(userId, postId, PostSource.CAMERA, Instant.now(), "UTC")

        coVerify(exactly = 1) { activityDao.recordEventIdempotent(userId, ActivityEventType.STREAK, today, 5) }
    }

    @Test
    fun `onPostCreated does not write a second STREAK activity event for a second post the same day`() = runTest {
        val today = LocalDate.now(ZoneOffset.UTC)
        // Already advanced earlier today: lastStreakDate == today both before and after this call
        // (advanceStreak's own no-op guard means the "after" read is unchanged from "before").
        coEvery { postDao.countCameraPostsOnDay(userId, any(), any()) } returns 2L
        coEvery { userDao.getUserById(userId) } returns testUser(currentStreak = 5, lastStreakDate = today)

        service.onPostCreated(userId, postId, PostSource.CAMERA, Instant.now(), "UTC")

        coVerify(exactly = 0) { activityDao.recordEventIdempotent(any(), any(), any(), any()) }
    }

    @Test
    fun `onPostCreated at daily cap still writes a STREAK activity event when the streak advances`() = runTest {
        val today = LocalDate.now(ZoneOffset.UTC)
        val yesterday = today.minusDays(1)
        // priorCount = cap + 1 means priorRewarded = cap → skip points, but streak still advances.
        coEvery { postDao.countCameraPostsOnDay(userId, any(), any()) } returns (ScoringServiceImpl.DAILY_CAMERA_CAP + 1).toLong()
        coEvery { userDao.getUserById(userId) } returnsMany listOf(
            testUser(currentStreak = 9, lastStreakDate = yesterday),
            testUser(currentStreak = 10, lastStreakDate = today),
        )

        service.onPostCreated(userId, postId, PostSource.CAMERA, Instant.now(), "UTC")

        coVerify(exactly = 0) { scoringDao.applyCreationPoints(any(), any(), any()) }
        coVerify(exactly = 1) { activityDao.recordEventIdempotent(userId, ActivityEventType.STREAK, today, 10) }
    }

    @Test
    fun `onPostCreated CAMERA passes createdAtTimezone to advanceStreak`() = runTest {
        coEvery { postDao.countCameraPostsOnDay(userId, any(), any()) } returns 1L

        service.onPostCreated(userId, postId, PostSource.CAMERA, Instant.now(), "Europe/Bucharest")

        coVerify(exactly = 1) { userDao.advanceStreak(userId, any(), "Europe/Bucharest") }
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
