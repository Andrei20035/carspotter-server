package dao

import com.carspotter.features.leaderboard.LeaderboardDAO
import com.carspotter.features.user.UserTable
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import testutils.TestDatabaseFactory
import testutils.UserTestSeed
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LeaderboardDaoTest {

    private val dao = LeaderboardDAO()

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
    fun `getUserRank returns 1 for single user`() = runTest {
        val cred = UserTestSeed.seedAuthCredential("alice@example.com")
        val userId = UserTestSeed.seedUser(cred.authCredentialId, username = "alice")
        setScore(userId, 100)

        assertEquals(1, dao.getUserRank(userId))
    }

    @Test
    fun `getUserRank returns correct unique positions when scores are distinct`() = runTest {
        val c1 = UserTestSeed.seedAuthCredential("a@example.com")
        val c2 = UserTestSeed.seedAuthCredential("b@example.com")
        val c3 = UserTestSeed.seedAuthCredential("c@example.com")
        val u1 = UserTestSeed.seedUser(c1.authCredentialId, username = "alice")
        val u2 = UserTestSeed.seedUser(c2.authCredentialId, username = "bob")
        val u3 = UserTestSeed.seedUser(c3.authCredentialId, username = "charlie")
        setScore(u1, 300)
        setScore(u2, 200)
        setScore(u3, 100)

        assertEquals(1, dao.getUserRank(u1))
        assertEquals(2, dao.getUserRank(u2))
        assertEquals(3, dao.getUserRank(u3))
    }

    @Test
    fun `getUserRank returns unique sequential positions for tied scores — tie-broken by userId ASC`() = runTest {
        val c1 = UserTestSeed.seedAuthCredential("a@example.com")
        val c2 = UserTestSeed.seedAuthCredential("b@example.com")
        val c3 = UserTestSeed.seedAuthCredential("c@example.com")
        val u1 = UserTestSeed.seedUser(c1.authCredentialId, username = "alice")
        val u2 = UserTestSeed.seedUser(c2.authCredentialId, username = "bob")
        val u3 = UserTestSeed.seedUser(c3.authCredentialId, username = "charlie")
        setScore(u1, 0)
        setScore(u2, 0)
        setScore(u3, 0)

        val r1 = dao.getUserRank(u1)
        val r2 = dao.getUserRank(u2)
        val r3 = dao.getUserRank(u3)

        // All three ranks are distinct and contiguous — no two users share a rank, no gaps.
        assertEquals(setOf(1, 2, 3), setOf(r1, r2, r3))
        // The user whose UUID sorts first in DB order (toString lexicographic) gets rank 1.
        val sorted = listOf(u1 to r1, u2 to r2, u3 to r3).sortedBy { it.first.toString() }
        assertEquals(1, sorted[0].second)
        assertEquals(2, sorted[1].second)
        assertEquals(3, sorted[2].second)
    }

    @Test
    fun `getUserRank for user with higher score than others below returns correct position`() = runTest {
        val c1 = UserTestSeed.seedAuthCredential("a@example.com")
        val c2 = UserTestSeed.seedAuthCredential("b@example.com")
        val c3 = UserTestSeed.seedAuthCredential("c@example.com")
        val u1 = UserTestSeed.seedUser(c1.authCredentialId, username = "alice")
        val u2 = UserTestSeed.seedUser(c2.authCredentialId, username = "bob")
        val u3 = UserTestSeed.seedUser(c3.authCredentialId, username = "charlie")
        setScore(u1, 50)
        setScore(u2, 100) // highest
        setScore(u3, 0)

        assertEquals(1, dao.getUserRank(u2))
        assertEquals(2, dao.getUserRank(u1))
        assertEquals(3, dao.getUserRank(u3))
    }

    @Test
    fun `getUserRank returns MAX_VALUE for unknown userId`() = runTest {
        assertEquals(Int.MAX_VALUE, dao.getUserRank(UUID.randomUUID()))
    }
}
