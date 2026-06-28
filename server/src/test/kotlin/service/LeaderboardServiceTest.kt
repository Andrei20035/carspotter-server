package service

import com.carspotter.core.storage.IStorageService
import com.carspotter.features.leaderboard.ILeaderboardDAO
import com.carspotter.features.leaderboard.ILeaderboardSnapshotDAO
import com.carspotter.features.leaderboard.LeaderboardService
import com.carspotter.features.leaderboard.RawLeaderboardEntry
import com.carspotter.features.leaderboard.UserScoreStreak
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class LeaderboardServiceTest {

    private val leaderboardDao = mockk<ILeaderboardDAO>()
    private val snapshotDao = mockk<ILeaderboardSnapshotDAO>()
    private val storage = mockk<IStorageService>(relaxed = true)

    private val service = LeaderboardService(leaderboardDao, snapshotDao, storage)

    private val userId = UUID.randomUUID()

    private fun userScoreStreak(
        streak: Int = 0,
        lastDate: LocalDate? = null,
        lastTz: String? = null,
        score: Int = 0,
    ) = UserScoreStreak(
        userId = userId,
        username = "alice",
        profilePicturePath = null,
        spotScore = score,
        currentStreak = streak,
        lastStreakDate = lastDate,
        lastStreakTimezone = lastTz,
    )

    @Test
    fun `movement is KEEP when no previous snapshot`() = runTest {
        coEvery { leaderboardDao.getTopEntries(any()) } returns emptyList()
        coEvery { leaderboardDao.getUserScoreAndStreak(userId) } returns userScoreStreak()
        coEvery { leaderboardDao.getUserRank(userId) } returns 1
        coEvery { snapshotDao.getPreviousRank(userId, any()) } returns null

        val result = service.getLeaderboard(userId, 50)

        assertEquals("KEEP", result.currentUser.movement)
        assertEquals(0, result.currentUser.placesMoved)
    }

    @Test
    fun `movement is UP when rank improved`() = runTest {
        coEvery { leaderboardDao.getTopEntries(any()) } returns emptyList()
        coEvery { leaderboardDao.getUserScoreAndStreak(userId) } returns userScoreStreak()
        coEvery { leaderboardDao.getUserRank(userId) } returns 3
        coEvery { snapshotDao.getPreviousRank(userId, any()) } returns 7

        val result = service.getLeaderboard(userId, 50)

        assertEquals("UP", result.currentUser.movement)
        assertEquals(4, result.currentUser.placesMoved)
    }

    @Test
    fun `movement is DOWN when rank dropped`() = runTest {
        coEvery { leaderboardDao.getTopEntries(any()) } returns emptyList()
        coEvery { leaderboardDao.getUserScoreAndStreak(userId) } returns userScoreStreak()
        coEvery { leaderboardDao.getUserRank(userId) } returns 8
        coEvery { snapshotDao.getPreviousRank(userId, any()) } returns 5

        val result = service.getLeaderboard(userId, 50)

        assertEquals("DOWN", result.currentUser.movement)
        assertEquals(3, result.currentUser.placesMoved)
    }

    @Test
    fun `streakDays is 0 when lastStreakDate is null`() = runTest {
        coEvery { leaderboardDao.getTopEntries(any()) } returns emptyList()
        coEvery { leaderboardDao.getUserScoreAndStreak(userId) } returns userScoreStreak(streak = 10, lastDate = null)
        coEvery { leaderboardDao.getUserRank(userId) } returns 1
        coEvery { snapshotDao.getPreviousRank(userId, any()) } returns null

        val result = service.getLeaderboard(userId, 50)

        assertEquals(0, result.currentUser.entry.streakDays)
    }

    @Test
    fun `streakDays is 0 when streak expired`() = runTest {
        val expired = LocalDate.now().minusDays(5)
        coEvery { leaderboardDao.getTopEntries(any()) } returns emptyList()
        coEvery { leaderboardDao.getUserScoreAndStreak(userId) } returns
            userScoreStreak(streak = 7, lastDate = expired, lastTz = "UTC")
        coEvery { leaderboardDao.getUserRank(userId) } returns 1
        coEvery { snapshotDao.getPreviousRank(userId, any()) } returns null

        val result = service.getLeaderboard(userId, 50)

        assertEquals(0, result.currentUser.entry.streakDays)
    }

    @Test
    fun `streakDays is correct when streak is alive (yesterday)`() = runTest {
        val yesterday = LocalDate.now().minusDays(1)
        coEvery { leaderboardDao.getTopEntries(any()) } returns emptyList()
        coEvery { leaderboardDao.getUserScoreAndStreak(userId) } returns
            userScoreStreak(streak = 5, lastDate = yesterday, lastTz = "UTC")
        coEvery { leaderboardDao.getUserRank(userId) } returns 1
        coEvery { snapshotDao.getPreviousRank(userId, any()) } returns null

        val result = service.getLeaderboard(userId, 50)

        assertEquals(5, result.currentUser.entry.streakDays)
    }

    @Test
    fun `entries list has sequential unique ranks — position in DAO-ordered list`() = runTest {
        val user1 = UUID.randomUUID()
        val user2 = UUID.randomUUID()
        val user3 = UUID.randomUUID()

        // DAO returns already-sorted list; service assigns rank = index + 1.
        val rawEntries = listOf(
            RawLeaderboardEntry(user1, "alice",   null, 200, 0, null, null),
            RawLeaderboardEntry(user2, "bob",     null, 200, 0, null, null),
            RawLeaderboardEntry(user3, "charlie", null, 100, 0, null, null),
        )
        coEvery { leaderboardDao.getTopEntries(any()) } returns rawEntries
        coEvery { leaderboardDao.getUserScoreAndStreak(userId) } returns userScoreStreak(score = 200)
        coEvery { leaderboardDao.getUserRank(userId) } returns 1
        coEvery { snapshotDao.getPreviousRank(userId, any()) } returns null

        val result = service.getLeaderboard(userId, 50)

        assertEquals(1, result.entries[0].rank) // alice   — position 1
        assertEquals(2, result.entries[1].rank) // bob     — position 2
        assertEquals(3, result.entries[2].rank) // charlie — position 3
    }

    @Test
    fun `entries list preserves DAO order and assigns sequential ranks`() = runTest {
        // DAO contract: spotScore DESC, id ASC — service must not reorder.
        val firstId  = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val secondId = UUID.fromString("00000000-0000-0000-0000-000000000002")

        val rawEntries = listOf(
            RawLeaderboardEntry(firstId,  "alpha", null, 200, 0, null, null),
            RawLeaderboardEntry(secondId, "beta",  null, 200, 0, null, null),
        )
        coEvery { leaderboardDao.getTopEntries(any()) } returns rawEntries
        coEvery { leaderboardDao.getUserScoreAndStreak(userId) } returns userScoreStreak()
        coEvery { leaderboardDao.getUserRank(userId) } returns 1
        coEvery { snapshotDao.getPreviousRank(userId, any()) } returns null

        val result = service.getLeaderboard(userId, 50)

        assertEquals(firstId,  result.entries[0].userId)
        assertEquals(secondId, result.entries[1].userId)
        assertEquals(1, result.entries[0].rank) // position 1
        assertEquals(2, result.entries[1].rank) // position 2
    }

    @Test
    fun `entries list has streakDays computed per-user`() = runTest {
        val otherId = UUID.randomUUID()
        val yesterday = LocalDate.now().minusDays(1)
        val expired = LocalDate.now().minusDays(10)

        val rawEntries = listOf(
            RawLeaderboardEntry(
                userId = userId,
                username = "alice",
                profilePicturePath = null,
                spotScore = 100,
                currentStreak = 5,
                lastStreakDate = yesterday,
                lastStreakTimezone = "UTC",
            ),
            RawLeaderboardEntry(
                userId = otherId,
                username = "bob",
                profilePicturePath = null,
                spotScore = 50,
                currentStreak = 3,
                lastStreakDate = expired,
                lastStreakTimezone = "UTC",
            ),
        )
        coEvery { leaderboardDao.getTopEntries(any()) } returns rawEntries
        coEvery { leaderboardDao.getUserScoreAndStreak(userId) } returns userScoreStreak()
        coEvery { leaderboardDao.getUserRank(userId) } returns 1
        coEvery { snapshotDao.getPreviousRank(userId, any()) } returns null

        val result = service.getLeaderboard(userId, 50)

        assertEquals(5, result.entries[0].streakDays) // alice — alive
        assertEquals(0, result.entries[1].streakDays) // bob — expired
    }
}
