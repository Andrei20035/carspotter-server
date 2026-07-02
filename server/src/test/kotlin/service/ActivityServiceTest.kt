package service

import com.carspotter.core.storage.IStorageService
import com.carspotter.features.activity.ActivityEventType
import com.carspotter.features.activity.ActivityService
import com.carspotter.features.leaderboard.ILeaderboardDAO
import com.carspotter.features.leaderboard.ILeaderboardSnapshotDAO
import com.carspotter.features.post.IPostDAO
import features.activity.ActivityEventRow
import features.activity.CommentActivityRow
import features.activity.IActivityDAO
import features.activity.LikeActivityRow
import com.carspotter.features.leaderboard.UserScoreStreak
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class ActivityServiceTest {

    private val activityDao = mockk<IActivityDAO>()
    private val snapshotDao = mockk<ILeaderboardSnapshotDAO>()
    private val leaderboardDao = mockk<ILeaderboardDAO>()
    private val postDao = mockk<IPostDAO>()
    private val storage = mockk<IStorageService>(relaxed = true)

    private val service = ActivityService(activityDao, snapshotDao, leaderboardDao, postDao, storage, "UTC")

    private val userId = UUID.randomUUID()

    private fun userScoreStreak(score: Int) = UserScoreStreak(
        userId = userId,
        username = "alice",
        profilePicturePath = null,
        spotScore = score,
        currentStreak = 0,
        lastStreakDate = null,
        lastStreakTimezone = null,
    )

    /** Stubs every source ActivityService merges into `items`, all empty by default. */
    private fun stubEmptySources() {
        coEvery { activityDao.getLikeItems(userId, any()) } returns emptyList()
        coEvery { activityDao.getCommentItems(userId, any()) } returns emptyList()
        coEvery { activityDao.getPersistedEvents(userId, any()) } returns emptyList()
        coEvery { activityDao.countTodayUniqueInteractors(userId, any()) } returns 0L
    }

    // ---------- weeklySpotScore ----------

    @Test
    fun `weeklySpotScore is the delta between current score and the week-start snapshot baseline`() = runTest {
        stubEmptySources()
        coEvery { leaderboardDao.getUserScoreAndStreak(userId) } returns userScoreStreak(score = 340)
        coEvery { snapshotDao.getSpotScoreOnOrBefore(userId, any()) } returns 100

        val result = service.getActivity(userId, 50, "UTC")

        assertEquals(240, result.weeklySpotScore)
    }

    @Test
    fun `weeklySpotScore clamps to 0 when score dropped below the baseline`() = runTest {
        stubEmptySources()
        coEvery { leaderboardDao.getUserScoreAndStreak(userId) } returns userScoreStreak(score = 50)
        coEvery { snapshotDao.getSpotScoreOnOrBefore(userId, any()) } returns 100

        val result = service.getActivity(userId, 50, "UTC")

        assertEquals(0, result.weeklySpotScore)
    }

    @Test
    fun `weeklySpotScore falls back to summing post points since week start when no snapshot exists`() = runTest {
        stubEmptySources()
        coEvery { leaderboardDao.getUserScoreAndStreak(userId) } returns userScoreStreak(score = 340)
        coEvery { snapshotDao.getSpotScoreOnOrBefore(userId, any()) } returns null
        coEvery { postDao.sumPointsSince(userId, any()) } returns 42

        val result = service.getActivity(userId, 50, "UTC")

        assertEquals(42, result.weeklySpotScore)
    }

    @Test
    fun `getActivity does not throw and returns 200-shape response when no snapshot has ever run`() = runTest {
        stubEmptySources()
        coEvery { leaderboardDao.getUserScoreAndStreak(userId) } returns null
        coEvery { snapshotDao.getSpotScoreOnOrBefore(userId, any()) } returns null
        coEvery { postDao.sumPointsSince(userId, any()) } returns 0

        val result = service.getActivity(userId, 50, "UTC")

        assertEquals(0, result.weeklySpotScore)
        assertEquals(0, result.todayInteractions)
        assertTrue(result.items.isEmpty())
    }

    // ---------- todayInteractions ----------

    @Test
    fun `todayInteractions passes through the DAO's unique interactor count`() = runTest {
        stubEmptySources()
        coEvery { activityDao.countTodayUniqueInteractors(userId, any()) } returns 3L
        coEvery { leaderboardDao.getUserScoreAndStreak(userId) } returns userScoreStreak(score = 0)
        coEvery { snapshotDao.getSpotScoreOnOrBefore(userId, any()) } returns 0

        val result = service.getActivity(userId, 50, "UTC")

        assertEquals(3, result.todayInteractions)
    }

    // ---------- items: merge + sort + mapping ----------

    @Test
    fun `items from all four types are merged and sorted newest first`() = runTest {
        val now = Instant.now()
        coEvery { leaderboardDao.getUserScoreAndStreak(userId) } returns userScoreStreak(score = 0)
        coEvery { snapshotDao.getSpotScoreOnOrBefore(userId, any()) } returns 0
        coEvery { activityDao.countTodayUniqueInteractors(userId, any()) } returns 0L

        val likeRow = LikeActivityRow(
            likeId = UUID.randomUUID(),
            actorUserId = UUID.randomUUID(),
            actorUsername = "tommy82",
            actorProfilePicturePath = "avatars/tommy.jpg",
            postId = UUID.randomUUID(),
            postImageKey = "posts/porsche.jpg",
            brand = "Porsche",
            model = "GT3",
            createdAt = now.minus(2, ChronoUnit.HOURS),
        )
        val commentRow = CommentActivityRow(
            commentId = UUID.randomUUID(),
            actorUserId = UUID.randomUUID(),
            actorUsername = "charlotte_khan",
            actorProfilePicturePath = null,
            postId = UUID.randomUUID(),
            postImageKey = "posts/bmw.jpg",
            brand = "BMW",
            model = "M4",
            commentText = "Incredible spec, where did you find this?",
            createdAt = now.minus(4, ChronoUnit.HOURS),
        )
        val leaderboardUpRow = ActivityEventRow(
            id = UUID.randomUUID(),
            type = ActivityEventType.LEADERBOARD_UP,
            valueInt = 3,
            createdAt = now.minus(1, ChronoUnit.DAYS),
        )
        val streakRow = ActivityEventRow(
            id = UUID.randomUUID(),
            type = ActivityEventType.STREAK,
            valueInt = 5,
            createdAt = now.minus(2, ChronoUnit.DAYS),
        )

        coEvery { activityDao.getLikeItems(userId, any()) } returns listOf(likeRow)
        coEvery { activityDao.getCommentItems(userId, any()) } returns listOf(commentRow)
        coEvery { activityDao.getPersistedEvents(userId, any()) } returns listOf(leaderboardUpRow, streakRow)

        val result = service.getActivity(userId, 50, "UTC")

        assertEquals(4, result.items.size)
        assertEquals(listOf("LIKE", "COMMENT", "LEADERBOARD_UP", "STREAK"), result.items.map { it.type })
    }

    @Test
    fun `LIKE item is mapped with bold-username fields, brand, model and thumbnail`() = runTest {
        stubEmptySources()
        coEvery { leaderboardDao.getUserScoreAndStreak(userId) } returns userScoreStreak(score = 0)
        coEvery { snapshotDao.getSpotScoreOnOrBefore(userId, any()) } returns 0

        val actorId = UUID.randomUUID()
        val postId = UUID.randomUUID()
        val likeRow = LikeActivityRow(
            likeId = UUID.randomUUID(),
            actorUserId = actorId,
            actorUsername = "tommy82",
            actorProfilePicturePath = "avatars/tommy.jpg",
            postId = postId,
            postImageKey = "posts/porsche.jpg",
            brand = "Porsche",
            model = "GT3",
            createdAt = Instant.now(),
        )
        coEvery { activityDao.getLikeItems(userId, any()) } returns listOf(likeRow)

        val result = service.getActivity(userId, 50, "UTC")

        val item = result.items.single()
        assertEquals("LIKE", item.type)
        assertEquals(actorId, item.actorUserId)
        assertEquals("tommy82", item.actorUsername)
        assertEquals(postId, item.postId)
        assertEquals("Porsche", item.brand)
        assertEquals("GT3", item.model)
    }

    @Test
    fun `COMMENT item carries the comment text`() = runTest {
        stubEmptySources()
        coEvery { leaderboardDao.getUserScoreAndStreak(userId) } returns userScoreStreak(score = 0)
        coEvery { snapshotDao.getSpotScoreOnOrBefore(userId, any()) } returns 0

        val commentRow = CommentActivityRow(
            commentId = UUID.randomUUID(),
            actorUserId = UUID.randomUUID(),
            actorUsername = "charlotte_khan",
            actorProfilePicturePath = null,
            postId = UUID.randomUUID(),
            postImageKey = "posts/bmw.jpg",
            brand = "BMW",
            model = "M4",
            commentText = "Incredible spec, where did you find this?",
            createdAt = Instant.now(),
        )
        coEvery { activityDao.getCommentItems(userId, any()) } returns listOf(commentRow)

        val result = service.getActivity(userId, 50, "UTC")

        val item = result.items.single()
        assertEquals("COMMENT", item.type)
        assertEquals("Incredible spec, where did you find this?", item.commentText)
    }

    @Test
    fun `LEADERBOARD_UP item exposes placesMoved when the persisted event reflects a multi-place jump`() = runTest {
        stubEmptySources()
        coEvery { leaderboardDao.getUserScoreAndStreak(userId) } returns userScoreStreak(score = 0)
        coEvery { snapshotDao.getSpotScoreOnOrBefore(userId, any()) } returns 0

        val eventId = UUID.randomUUID()
        coEvery { activityDao.getPersistedEvents(userId, any()) } returns listOf(
            ActivityEventRow(id = eventId, type = ActivityEventType.LEADERBOARD_UP, valueInt = 3, createdAt = Instant.now()),
        )

        val result = service.getActivity(userId, 50, "UTC")

        val item = result.items.single()
        assertEquals("LEADERBOARD_UP", item.type)
        assertEquals("lb:$eventId", item.id)
        assertEquals(3, item.placesMoved)
    }

    @Test
    fun `STREAK item exposes streakDays`() = runTest {
        stubEmptySources()
        coEvery { leaderboardDao.getUserScoreAndStreak(userId) } returns userScoreStreak(score = 0)
        coEvery { snapshotDao.getSpotScoreOnOrBefore(userId, any()) } returns 0

        val eventId = UUID.randomUUID()
        coEvery { activityDao.getPersistedEvents(userId, any()) } returns listOf(
            ActivityEventRow(id = eventId, type = ActivityEventType.STREAK, valueInt = 5, createdAt = Instant.now()),
        )

        val result = service.getActivity(userId, 50, "UTC")

        val item = result.items.single()
        assertEquals("STREAK", item.type)
        assertEquals("streak:$eventId", item.id)
        assertEquals(5, item.streakDays)
    }

    @Test
    fun `items are truncated to limit after merge-sort`() = runTest {
        coEvery { leaderboardDao.getUserScoreAndStreak(userId) } returns userScoreStreak(score = 0)
        coEvery { snapshotDao.getSpotScoreOnOrBefore(userId, any()) } returns 0
        coEvery { activityDao.countTodayUniqueInteractors(userId, any()) } returns 0L
        coEvery { activityDao.getCommentItems(userId, any()) } returns emptyList()

        val now = Instant.now()
        val likeRows = (1..5).map { i ->
            LikeActivityRow(
                likeId = UUID.randomUUID(),
                actorUserId = UUID.randomUUID(),
                actorUsername = "user$i",
                actorProfilePicturePath = null,
                postId = UUID.randomUUID(),
                postImageKey = "posts/$i.jpg",
                brand = "Brand$i",
                model = "Model$i",
                createdAt = now.minus(i.toLong(), ChronoUnit.HOURS),
            )
        }
        coEvery { activityDao.getLikeItems(userId, any()) } returns likeRows
        coEvery { activityDao.getPersistedEvents(userId, any()) } returns emptyList()

        val result = service.getActivity(userId, 2, "UTC")

        assertEquals(2, result.items.size)
        // Newest two (i=1, i=2) survive the truncation.
        assertEquals("user1", result.items[0].actorUsername)
        assertEquals("user2", result.items[1].actorUsername)
    }
}
