package routes

import com.carspotter.features.leaderboard.LeaderboardSnapshotTable
import com.carspotter.features.user.UserTable
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.selectAll
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
import testutils.setTestEnv
import testutils.stopKoinSafely
import testutils.testAdminLeaderboardModule
import java.time.LocalDate

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdminLeaderboardRoutesTest {

    private val validToken = "test-admin-secret"

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

    private fun adminTest(block: suspend ApplicationTestBuilder.(io.ktor.client.HttpClient) -> Unit) =
        testApplication {
            application { testAdminLeaderboardModule(validToken) }
            val client = createClient {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true; isLenient = true })
                }
            }
            block(client)
        }

    @Test
    fun `POST snapshot with valid token returns 200 and writes rows`() = adminTest { client ->
        val alice = CommentTestSeed.seedUser("alice")
        transaction {
            UserTable.update({ UserTable.id eq alice.userId }) {
                it[UserTable.spotScore] = 100
            }
        }

        val resp = client.post("/api/admin/leaderboard/snapshot") {
            header("X-Admin-Token", validToken)
        }

        assertEquals(HttpStatusCode.OK, resp.status)
        val count = transaction {
            LeaderboardSnapshotTable.selectAll().count()
        }
        assertEquals(1L, count)
    }

    @Test
    fun `POST snapshot without token returns 401`() = adminTest { client ->
        val resp = client.post("/api/admin/leaderboard/snapshot")
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `POST snapshot with wrong token returns 401`() = adminTest { client ->
        val resp = client.post("/api/admin/leaderboard/snapshot") {
            header("X-Admin-Token", "wrong-token")
        }
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `POST snapshot is idempotent — second call for same date does not duplicate rows`() = adminTest { client ->
        CommentTestSeed.seedUser("alice")

        client.post("/api/admin/leaderboard/snapshot") {
            header("X-Admin-Token", validToken)
        }
        client.post("/api/admin/leaderboard/snapshot") {
            header("X-Admin-Token", validToken)
        }

        val today = LocalDate.now()
        val count = transaction {
            LeaderboardSnapshotTable.selectAll()
                .where { LeaderboardSnapshotTable.snapshotDate eq today }
                .count()
        }
        assertEquals(1L, count)
    }
}
