package dao

import com.carspotter.features.post.PostDAO
import com.carspotter.features.post.PostTable
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
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
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostDaoSumPointsSinceTest {

    private val dao = PostDAO()

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

    private fun seedPost(authorId: UUID, points: Int, createdAt: Instant): UUID = transaction {
        val postId = PostTable.insert {
            it[PostTable.userId] = authorId
            it[PostTable.imageKey] = "posts/test.jpg"
            it[PostTable.carModelId] = null
            it[PostTable.customBrand] = "BMW"
            it[PostTable.customModel] = "M3"
            it[PostTable.points] = points
        }[PostTable.id].value

        // createdAt has a DB default (CurrentTimestamp) that can't be set on insert directly here,
        // so we override it explicitly to control the time window under test.
        PostTable.update({ PostTable.id eq postId }) {
            it[PostTable.createdAt] = createdAt
        }

        postId
    }

    private fun seedUser(username: String = "alice"): UUID {
        val cred = UserTestSeed.seedAuthCredential("$username@example.com")
        return UserTestSeed.seedUser(cred.authCredentialId, username = username)
    }

    @Test
    fun `sumPointsSince returns 0 when user has no posts`() = runTest {
        val userId = seedUser()

        val sum = dao.sumPointsSince(userId, Instant.now().minus(7, ChronoUnit.DAYS))

        assertEquals(0, sum)
    }

    @Test
    fun `sumPointsSince sums points for posts on or after since`() = runTest {
        val userId = seedUser()
        val now = Instant.now()
        seedPost(userId, points = 10, createdAt = now.minus(1, ChronoUnit.DAYS))
        seedPost(userId, points = 5, createdAt = now.minus(2, ChronoUnit.DAYS))

        val sum = dao.sumPointsSince(userId, now.minus(7, ChronoUnit.DAYS))

        assertEquals(15, sum)
    }

    @Test
    fun `sumPointsSince excludes posts created before since`() = runTest {
        val userId = seedUser()
        val now = Instant.now()
        seedPost(userId, points = 10, createdAt = now.minus(1, ChronoUnit.DAYS))
        seedPost(userId, points = 99, createdAt = now.minus(10, ChronoUnit.DAYS))

        val sum = dao.sumPointsSince(userId, now.minus(3, ChronoUnit.DAYS))

        assertEquals(10, sum)
    }

    @Test
    fun `sumPointsSince includes a post created exactly at since`() = runTest {
        val userId = seedUser()
        val since = Instant.now().minus(3, ChronoUnit.DAYS)
        seedPost(userId, points = 7, createdAt = since)

        val sum = dao.sumPointsSince(userId, since)

        assertEquals(7, sum)
    }

    @Test
    fun `sumPointsSince only counts the given user's posts`() = runTest {
        val alice = seedUser("alice")
        val bob = seedUser("bob")
        val now = Instant.now()
        seedPost(alice, points = 10, createdAt = now.minus(1, ChronoUnit.DAYS))
        seedPost(bob, points = 50, createdAt = now.minus(1, ChronoUnit.DAYS))

        val sum = dao.sumPointsSince(alice, now.minus(7, ChronoUnit.DAYS))

        assertEquals(10, sum)
    }
}
