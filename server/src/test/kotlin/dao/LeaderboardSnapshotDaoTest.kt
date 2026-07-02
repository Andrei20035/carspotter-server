package dao

import com.carspotter.features.activity.ActivityEventType
import com.carspotter.features.leaderboard.LeaderboardSnapshotDAO
import com.carspotter.features.leaderboard.LeaderboardSnapshotTable
import com.carspotter.features.user.UserTable
import features.activity.ActivityDAO
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import testutils.TestDatabaseFactory
import testutils.UserTestSeed
import java.time.LocalDate
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LeaderboardSnapshotDaoTest {

    private val activityDao = ActivityDAO()
    private val dao = LeaderboardSnapshotDAO(activityDao)
    private val snapshotDate: LocalDate = LocalDate.of(2025, 6, 15)

    @BeforeAll
    fun setup() {
        TestDatabaseFactory.start()
    }

    @AfterAll
    fun tearDown() {
        TestDatabaseFactory.stop()
    }

    @BeforeEach
    fun clean() {
        TestDatabaseFactory.cleanDatabase()
    }

    private fun setScore(userId: UUID, score: Int) = transaction {
        UserTable.update({ UserTable.id eq userId }) {
            it[UserTable.spotScore] = score
        }
    }

    @Test
    fun `snapshotAllRanks returns 0 when no users exist`() = runTest {
        val rows = dao.snapshotAllRanks(snapshotDate)
        assertEquals(0, rows)
    }

    @Test
    fun `snapshotAllRanks writes correct rank for single user`() = runTest {
        val cred = UserTestSeed.seedAuthCredential("alice@example.com")
        val userId = UserTestSeed.seedUser(cred.authCredentialId, username = "alice")
        setScore(userId, 100)

        val rows = dao.snapshotAllRanks(snapshotDate)

        assertEquals(1, rows)
        val prevRank = dao.getPreviousRank(userId, snapshotDate.plusDays(1))
        assertEquals(1, prevRank)
    }

    @Test
    fun `snapshotAllRanks assigns distinct ranks when no ties`() = runTest {
        val c1 = UserTestSeed.seedAuthCredential("a@example.com")
        val c2 = UserTestSeed.seedAuthCredential("b@example.com")
        val c3 = UserTestSeed.seedAuthCredential("c@example.com")
        val u1 = UserTestSeed.seedUser(c1.authCredentialId, username = "alice")
        val u2 = UserTestSeed.seedUser(c2.authCredentialId, username = "bob")
        val u3 = UserTestSeed.seedUser(c3.authCredentialId, username = "charlie")
        setScore(u1, 300)
        setScore(u2, 200)
        setScore(u3, 100)

        dao.snapshotAllRanks(snapshotDate)

        val tomorrow = snapshotDate.plusDays(1)
        assertEquals(1, dao.getPreviousRank(u1, tomorrow))
        assertEquals(2, dao.getPreviousRank(u2, tomorrow))
        assertEquals(3, dao.getPreviousRank(u3, tomorrow))
    }

    @Test
    fun `snapshotAllRanks gives tied users distinct sequential ranks broken by userId`() = runTest {
        val c1 = UserTestSeed.seedAuthCredential("a@example.com")
        val c2 = UserTestSeed.seedAuthCredential("b@example.com")
        val c3 = UserTestSeed.seedAuthCredential("c@example.com")
        val u1 = UserTestSeed.seedUser(c1.authCredentialId, username = "alice")
        val u2 = UserTestSeed.seedUser(c2.authCredentialId, username = "bob")
        val u3 = UserTestSeed.seedUser(c3.authCredentialId, username = "charlie")
        setScore(u1, 200)
        setScore(u2, 200) // tie with u1 — tie-broken by userId ASC
        setScore(u3, 100)

        dao.snapshotAllRanks(snapshotDate)

        val tomorrow = snapshotDate.plusDays(1)
        val r1 = dao.getPreviousRank(u1, tomorrow)!!
        val r2 = dao.getPreviousRank(u2, tomorrow)!!
        // Tied users get distinct sequential ranks 1 and 2 (order decided by userId ASC).
        assertEquals(setOf(1, 2), setOf(r1, r2))
        // User with the smaller UUID (toString lexicographic, matching DB order) sorts first → lower rank number.
        if (u1.toString() < u2.toString()) assertEquals(1, r1) else assertEquals(1, r2)
        // Charlie is always rank 3 — no skipped ranks.
        assertEquals(3, dao.getPreviousRank(u3, tomorrow))
    }

    @Test
    fun `snapshotAllRanks is idempotent — re-run does not duplicate rows`() = runTest {
        val cred = UserTestSeed.seedAuthCredential("alice@example.com")
        UserTestSeed.seedUser(cred.authCredentialId, username = "alice")

        dao.snapshotAllRanks(snapshotDate)
        dao.snapshotAllRanks(snapshotDate) // second run

        val count = transaction {
            LeaderboardSnapshotTable.selectAll()
                .where { LeaderboardSnapshotTable.snapshotDate eq snapshotDate }
                .count()
        }
        assertEquals(1L, count)
    }

    @Test
    fun `getPreviousRank returns null when no snapshot exists`() = runTest {
        val cred = UserTestSeed.seedAuthCredential("alice@example.com")
        val userId = UserTestSeed.seedUser(cred.authCredentialId, username = "alice")

        assertNull(dao.getPreviousRank(userId, snapshotDate))
    }

    @Test
    fun `getPreviousRank returns the latest snapshot strictly before beforeDate`() = runTest {
        val cred = UserTestSeed.seedAuthCredential("alice@example.com")
        val userId = UserTestSeed.seedUser(cred.authCredentialId, username = "alice")
        setScore(userId, 50)

        val day1 = LocalDate.of(2025, 6, 13)
        val day2 = LocalDate.of(2025, 6, 14)
        val day3 = LocalDate.of(2025, 6, 15) // today — should NOT be returned

        dao.snapshotAllRanks(day1)
        dao.snapshotAllRanks(day2)
        dao.snapshotAllRanks(day3)

        // beforeDate = day3 → latest strictly-before = day2
        assertEquals(1, dao.getPreviousRank(userId, day3))
    }

    @Test
    fun `getPreviousRank returns null for unknown user`() = runTest {
        val cred = UserTestSeed.seedAuthCredential("alice@example.com")
        UserTestSeed.seedUser(cred.authCredentialId, username = "alice")
        dao.snapshotAllRanks(snapshotDate)

        assertNull(dao.getPreviousRank(UUID.randomUUID(), snapshotDate.plusDays(1)))
    }

    // ---------- getSpotScoreOnOrBefore ----------

    @Test
    fun `getSpotScoreOnOrBefore returns null when no snapshot exists`() = runTest {
        val cred = UserTestSeed.seedAuthCredential("alice@example.com")
        val userId = UserTestSeed.seedUser(cred.authCredentialId, username = "alice")

        assertNull(dao.getSpotScoreOnOrBefore(userId, snapshotDate))
    }

    @Test
    fun `getSpotScoreOnOrBefore returns the score from a snapshot exactly on date`() = runTest {
        val cred = UserTestSeed.seedAuthCredential("alice@example.com")
        val userId = UserTestSeed.seedUser(cred.authCredentialId, username = "alice")
        setScore(userId, 120)

        dao.snapshotAllRanks(snapshotDate)

        assertEquals(120, dao.getSpotScoreOnOrBefore(userId, snapshotDate))
    }

    @Test
    fun `getSpotScoreOnOrBefore returns the latest snapshot on or before date, ignoring later ones`() = runTest {
        val cred = UserTestSeed.seedAuthCredential("alice@example.com")
        val userId = UserTestSeed.seedUser(cred.authCredentialId, username = "alice")

        val monday = LocalDate.of(2025, 6, 9)
        val wednesday = LocalDate.of(2025, 6, 11)
        val friday = LocalDate.of(2025, 6, 13)

        setScore(userId, 50)
        dao.snapshotAllRanks(monday)
        setScore(userId, 90)
        dao.snapshotAllRanks(wednesday)
        setScore(userId, 200)
        dao.snapshotAllRanks(friday)

        // Query "on or before Wednesday" should pick Wednesday's baseline, not Friday's.
        assertEquals(90, dao.getSpotScoreOnOrBefore(userId, wednesday))
    }

    @Test
    fun `getSpotScoreOnOrBefore returns null for unknown user`() = runTest {
        val cred = UserTestSeed.seedAuthCredential("alice@example.com")
        UserTestSeed.seedUser(cred.authCredentialId, username = "alice")
        dao.snapshotAllRanks(snapshotDate)

        assertNull(dao.getSpotScoreOnOrBefore(UUID.randomUUID(), snapshotDate))
    }

    // ---------- LEADERBOARD_UP activity events ----------

    @Test
    fun `snapshotAllRanks persists a LEADERBOARD_UP event when a user moves up more than one place`() = runTest {
        val c1 = UserTestSeed.seedAuthCredential("a@example.com")
        val c2 = UserTestSeed.seedAuthCredential("b@example.com")
        val c3 = UserTestSeed.seedAuthCredential("c@example.com")
        val u1 = UserTestSeed.seedUser(c1.authCredentialId, username = "alice")
        val u2 = UserTestSeed.seedUser(c2.authCredentialId, username = "bob")
        val u3 = UserTestSeed.seedUser(c3.authCredentialId, username = "charlie")

        // Day 1: alice=3, bob=2, charlie=1 → charlie is rank 3.
        setScore(u1, 300)
        setScore(u2, 200)
        setScore(u3, 100)
        val day1 = snapshotDate
        dao.snapshotAllRanks(day1)

        // Day 2: charlie's score jumps past everyone → rank 3 → rank 1 (+2 places).
        setScore(u3, 500)
        val day2 = day1.plusDays(1)
        dao.snapshotAllRanks(day2)

        val events = activityDao.getPersistedEvents(u3, 10)
        assertEquals(1, events.size)
        assertEquals(ActivityEventType.LEADERBOARD_UP, events.single().type)
        assertEquals(2, events.single().valueInt)
    }

    @Test
    fun `snapshotAllRanks does not persist a LEADERBOARD_UP event for a single-place move up`() = runTest {
        val c1 = UserTestSeed.seedAuthCredential("a@example.com")
        val c2 = UserTestSeed.seedAuthCredential("b@example.com")
        val u1 = UserTestSeed.seedUser(c1.authCredentialId, username = "alice")
        val u2 = UserTestSeed.seedUser(c2.authCredentialId, username = "bob")

        // Day 1: alice=2 (rank 1), bob=1 (rank 2).
        setScore(u1, 200)
        setScore(u2, 100)
        val day1 = snapshotDate
        dao.snapshotAllRanks(day1)

        // Day 2: bob overtakes alice by a hair → rank 2 → rank 1 (+1 place only).
        setScore(u2, 250)
        val day2 = day1.plusDays(1)
        dao.snapshotAllRanks(day2)

        assertTrue(activityDao.getPersistedEvents(u2, 10).isEmpty())
    }

    @Test
    fun `snapshotAllRanks does not persist a LEADERBOARD_UP event when rank is unchanged or drops`() = runTest {
        val cred = UserTestSeed.seedAuthCredential("alice@example.com")
        val userId = UserTestSeed.seedUser(cred.authCredentialId, username = "alice")
        setScore(userId, 100)

        val day1 = snapshotDate
        val day2 = day1.plusDays(1)
        dao.snapshotAllRanks(day1)
        dao.snapshotAllRanks(day2) // still rank 1, no movement — single user, nothing to move past

        assertTrue(activityDao.getPersistedEvents(userId, 10).isEmpty())
    }

    @Test
    fun `snapshotAllRanks does not duplicate LEADERBOARD_UP events when re-run for the same date`() = runTest {
        val c1 = UserTestSeed.seedAuthCredential("a@example.com")
        val c2 = UserTestSeed.seedAuthCredential("b@example.com")
        val c3 = UserTestSeed.seedAuthCredential("c@example.com")
        val u1 = UserTestSeed.seedUser(c1.authCredentialId, username = "alice")
        val u2 = UserTestSeed.seedUser(c2.authCredentialId, username = "bob")
        val u3 = UserTestSeed.seedUser(c3.authCredentialId, username = "charlie")

        setScore(u1, 300)
        setScore(u2, 200)
        setScore(u3, 100)
        val day1 = snapshotDate
        dao.snapshotAllRanks(day1)

        setScore(u3, 500)
        val day2 = day1.plusDays(1)
        dao.snapshotAllRanks(day2)
        dao.snapshotAllRanks(day2) // re-run for the same date — must not duplicate the event

        val events = activityDao.getPersistedEvents(u3, 10)
        assertEquals(1, events.size)
    }
}
