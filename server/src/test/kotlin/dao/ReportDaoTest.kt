package dao

import com.carspotter.features.report.ReportReason
import features.report.ReportDAO
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import testutils.CommentTestSeed
import testutils.ReportTestSeed
import testutils.TestDatabaseFactory
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReportDaoTest {

    private val dao = ReportDAO()

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

    @Test
    fun `createReport inserts report correctly`() = runTest {
        val alice = CommentTestSeed.seedUser()
        val post = CommentTestSeed.seedPost(alice.userId)

        dao.createReport(alice.userId, post.postId, ReportReason.INCORRECT_CAR_MODEL)

        assertTrue(ReportTestSeed.reportExists(alice.userId, post.postId, ReportReason.INCORRECT_CAR_MODEL))
    }

    @Test
    fun `createReport returns the new report id`() = runTest {
        val alice = CommentTestSeed.seedUser()
        val post = CommentTestSeed.seedPost(alice.userId)

        val id = dao.createReport(alice.userId, post.postId, ReportReason.DUPLICATE_POST)

        assertEquals(1L, ReportTestSeed.countReports(post.postId))
        // id-ul returnat trebuie să fie un UUID valid (non-null prin tip)
        assertTrue(id.toString().isNotBlank())
    }

    @Test
    fun `createReport throws FK violation (23503) when postId does not exist`() {
        val alice = CommentTestSeed.seedUser()
        val ghostPostId = UUID.randomUUID()

        val ex = assertThrows(ExposedSQLException::class.java) {
            runBlocking { dao.createReport(alice.userId, ghostPostId, ReportReason.INAPPROPRIATE_CONTENT) }
        }
        assertEquals("23503", ex.sqlState)
    }

    @Test
    fun `createReport throws unique violation (23505) on duplicate user-post-reason`() {
        val alice = CommentTestSeed.seedUser()
        val post = CommentTestSeed.seedPost(alice.userId)

        runBlocking { dao.createReport(alice.userId, post.postId, ReportReason.DUPLICATE_POST) }

        val ex = assertThrows(ExposedSQLException::class.java) {
            runBlocking { dao.createReport(alice.userId, post.postId, ReportReason.DUPLICATE_POST) }
        }
        assertEquals("23505", ex.sqlState)
    }

    @Test
    fun `same user can report same post for different reasons`() = runTest {
        val alice = CommentTestSeed.seedUser()
        val post = CommentTestSeed.seedPost(alice.userId)

        dao.createReport(alice.userId, post.postId, ReportReason.DUPLICATE_POST)
        dao.createReport(alice.userId, post.postId, ReportReason.INAPPROPRIATE_CONTENT)

        assertEquals(2L, ReportTestSeed.countReports(post.postId))
    }

    @Test
    fun `different users can report same post for same reason`() = runTest {
        val alice = CommentTestSeed.seedUser(username = "alice")
        val bob = CommentTestSeed.seedUser(username = "bob", email = "bob@example.com")
        val post = CommentTestSeed.seedPost(alice.userId)

        dao.createReport(alice.userId, post.postId, ReportReason.INCORRECT_CAR_MODEL)
        dao.createReport(bob.userId, post.postId, ReportReason.INCORRECT_CAR_MODEL)

        assertEquals(2L, ReportTestSeed.countReports(post.postId))
    }

    @Test
    fun `cascade deleting post removes its reports`() = runTest {
        val alice = CommentTestSeed.seedUser()
        val post = CommentTestSeed.seedPost(alice.userId)
        ReportTestSeed.insertReport(alice.userId, post.postId, ReportReason.DUPLICATE_POST)

        transaction {
            com.carspotter.features.post.PostTable.deleteWhere {
                com.carspotter.features.post.PostTable.id eq post.postId
            }
        }

        assertEquals(0L, ReportTestSeed.countReports(post.postId))
    }

    @Test
    fun `cascade deleting user removes their reports`() = runTest {
        val alice = CommentTestSeed.seedUser()
        val post = CommentTestSeed.seedPost(alice.userId)
        ReportTestSeed.insertReport(alice.userId, post.postId, ReportReason.DUPLICATE_POST)

        transaction {
            com.carspotter.features.user.UserTable.deleteWhere {
                com.carspotter.features.user.UserTable.id eq alice.userId
            }
        }

        assertFalse(ReportTestSeed.reportExists(alice.userId, post.postId, ReportReason.DUPLICATE_POST))
    }
}
