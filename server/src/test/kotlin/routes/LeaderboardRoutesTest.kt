package routes

import com.carspotter.features.auth.JwtService
import com.carspotter.features.leaderboard.LeaderboardSnapshotDAO
import com.carspotter.features.leaderboard.dto.LeaderboardResponseDTO
import com.carspotter.features.user.UserTable
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import testutils.CommentTestSeed
import testutils.TestDatabaseFactory
import testutils.TestEnv
import testutils.setTestEnv
import testutils.stopKoinSafely
import testutils.testLeaderboardModule
import java.time.LocalDate
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LeaderboardRoutesTest {

    private val jwt = JwtService(
        jwtSecret = TestEnv.JWT_SECRET,
        jwtIssuer = TestEnv.JWT_ISSUER,
        jwtAudience = TestEnv.JWT_AUDIENCE,
    )

    private val snapshotDao = LeaderboardSnapshotDAO()

    @BeforeAll
    fun setup() {
        setTestEnv()
        TestDatabaseFactory.start()
    }

    @AfterAll
    fun tearDown() {
        TestDatabaseFactory.stop()
    }

    @BeforeEach
    fun clean() {
        TestDatabaseFactory.cleanDatabase()
        stopKoinSafely()
    }

    private fun tokenFor(authId: UUID, userId: UUID, email: String) =
        jwt.generateJwtToken(credentialId = authId, userId = userId, email = email)

    private fun setScore(userId: UUID, score: Int) = transaction {
        UserTable.update({ UserTable.id eq userId }) {
            it[UserTable.spotScore] = score
        }
    }

    private fun leaderboardTest(block: suspend ApplicationTestBuilder.(io.ktor.client.HttpClient) -> Unit) =
        testApplication {
            application { testLeaderboardModule() }
            val client = createClient {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true; isLenient = true })
                }
            }
            block(client)
        }

    @Test
    fun `GET leaderboard requires auth`() = leaderboardTest { client ->
        val resp = client.get("/api/leaderboard")
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `GET leaderboard returns empty entries list and KEEP movement for new user`() = leaderboardTest { client ->
        val alice = CommentTestSeed.seedUser("alice")
        val token = tokenFor(alice.authId, alice.userId, alice.email)

        val resp = client.get("/api/leaderboard") { bearerAuth(token) }

        assertEquals(HttpStatusCode.OK, resp.status)
        val body: LeaderboardResponseDTO = resp.body()
        assertEquals("KEEP", body.currentUser.movement)
        assertEquals(0, body.currentUser.placesMoved)
        assertEquals(0, body.currentUser.entry.streakDays)
    }

    @Test
    fun `GET leaderboard returns correct spotScore and rank`() = leaderboardTest { client ->
        val alice = CommentTestSeed.seedUser("alice")
        val bob = CommentTestSeed.seedUser("bob")
        setScore(alice.userId, 300)
        setScore(bob.userId, 100)
        val token = tokenFor(alice.authId, alice.userId, alice.email)

        val resp = client.get("/api/leaderboard") { bearerAuth(token) }

        assertEquals(HttpStatusCode.OK, resp.status)
        val body: LeaderboardResponseDTO = resp.body()
        assertEquals(1, body.currentUser.entry.rank)
        assertEquals(300, body.currentUser.entry.spotScore)
        assertEquals(2, body.entries.size)
        assertEquals(alice.userId, body.entries[0].userId)
        assertEquals(1, body.entries[0].rank)
        assertEquals(bob.userId, body.entries[1].userId)
        assertEquals(2, body.entries[1].rank)
    }

    @Test
    fun `GET leaderboard returns movement UP when rank improved since yesterday`() = leaderboardTest { client ->
        val alice = CommentTestSeed.seedUser("alice")
        val bob = CommentTestSeed.seedUser("bob")
        setScore(alice.userId, 100)
        setScore(bob.userId, 200) // bob was rank 1 yesterday, alice rank 2

        val yesterday = LocalDate.now().minusDays(1)
        kotlinx.coroutines.runBlocking { snapshotDao.snapshotAllRanks(yesterday) }

        // Now alice overtakes bob
        setScore(alice.userId, 300)
        setScore(bob.userId, 50)

        val token = tokenFor(alice.authId, alice.userId, alice.email)

        val resp = client.get("/api/leaderboard") { bearerAuth(token) }

        assertEquals(HttpStatusCode.OK, resp.status)
        val body: LeaderboardResponseDTO = resp.body()
        assertEquals("UP", body.currentUser.movement)
        assertEquals(1, body.currentUser.placesMoved)
    }

    @Test
    fun `GET leaderboard returns movement DOWN when rank dropped since yesterday`() = leaderboardTest { client ->
        val alice = CommentTestSeed.seedUser("alice")
        val bob = CommentTestSeed.seedUser("bob")
        setScore(alice.userId, 300) // alice was rank 1 yesterday
        setScore(bob.userId, 50)

        val yesterday = LocalDate.now().minusDays(1)
        kotlinx.coroutines.runBlocking { snapshotDao.snapshotAllRanks(yesterday) }

        // Now bob overtakes alice
        setScore(alice.userId, 50)
        setScore(bob.userId, 300)

        val token = tokenFor(alice.authId, alice.userId, alice.email)

        val resp = client.get("/api/leaderboard") { bearerAuth(token) }

        assertEquals(HttpStatusCode.OK, resp.status)
        val body: LeaderboardResponseDTO = resp.body()
        assertEquals("DOWN", body.currentUser.movement)
        assertEquals(1, body.currentUser.placesMoved)
    }

    @Test
    fun `GET leaderboard entries have distinct sequential ranks for tied scores broken by userId`() = leaderboardTest { client ->
        val alice = CommentTestSeed.seedUser("alice")
        val bob = CommentTestSeed.seedUser("bob")
        val charlie = CommentTestSeed.seedUser("charlie")
        // alice and bob tie at 200; charlie is below — tie-broken by userId ASC
        setScore(alice.userId, 200)
        setScore(bob.userId, 200)
        setScore(charlie.userId, 100)
        val token = tokenFor(alice.authId, alice.userId, alice.email)

        val resp = client.get("/api/leaderboard") { bearerAuth(token) }

        assertEquals(HttpStatusCode.OK, resp.status)
        val body: LeaderboardResponseDTO = resp.body()

        val aliceEntry = body.entries.find { it.userId == alice.userId }!!
        val bobEntry = body.entries.find { it.userId == bob.userId }!!
        val charlieEntry = body.entries.find { it.userId == charlie.userId }!!

        // Tied users get distinct sequential ranks 1 and 2 — no skips, no shared ranks.
        assertEquals(setOf(1, 2), setOf(aliceEntry.rank, bobEntry.rank))
        // User with the smaller UUID (toString lexicographic, matching DB order) sorts first → lower rank number.
        if (alice.userId.toString() < bob.userId.toString()) assertEquals(1, aliceEntry.rank) else assertEquals(1, bobEntry.rank)
        // Charlie is always rank 3.
        assertEquals(3, charlieEntry.rank)

        // currentUser.entry.rank must match alice's rank in the entries list.
        assertEquals(aliceEntry.rank, body.currentUser.entry.rank)
    }

    @Test
    fun `GET leaderboard returns streakDays 0 when last streak date is too old`() = leaderboardTest { client ->
        val alice = CommentTestSeed.seedUser("alice")
        // Manually set a streak that expired (lastStreakDate = 5 days ago)
        transaction {
            UserTable.update({ UserTable.id eq alice.userId }) {
                it[UserTable.currentStreak] = 10
                it[UserTable.lastStreakDate] = LocalDate.now().minusDays(5)
                it[UserTable.lastStreakTimezone] = "UTC"
            }
        }
        val token = tokenFor(alice.authId, alice.userId, alice.email)

        val resp = client.get("/api/leaderboard") { bearerAuth(token) }

        assertEquals(HttpStatusCode.OK, resp.status)
        val body: LeaderboardResponseDTO = resp.body()
        assertEquals(0, body.currentUser.entry.streakDays)
        // Also check the entries list
        val aliceEntry = body.entries.find { it.userId == alice.userId }
        assertEquals(0, aliceEntry?.streakDays)
    }

    @Test
    fun `GET leaderboard returns streakDays when last streak date is yesterday`() = leaderboardTest { client ->
        val alice = CommentTestSeed.seedUser("alice")
        transaction {
            UserTable.update({ UserTable.id eq alice.userId }) {
                it[UserTable.currentStreak] = 5
                it[UserTable.lastStreakDate] = LocalDate.now().minusDays(1)
                it[UserTable.lastStreakTimezone] = "UTC"
            }
        }
        val token = tokenFor(alice.authId, alice.userId, alice.email)

        val resp = client.get("/api/leaderboard") { bearerAuth(token) }

        assertEquals(HttpStatusCode.OK, resp.status)
        val body: LeaderboardResponseDTO = resp.body()
        assertEquals(5, body.currentUser.entry.streakDays)
    }
}
