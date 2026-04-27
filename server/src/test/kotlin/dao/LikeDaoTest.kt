package dao

import features.like.LikeDAO
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
import testutils.LikeTestSeed
import testutils.TestDatabaseFactory
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LikeDaoTest {

    private val dao = LikeDAO()

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

    // ---------- likePost ----------

    @Test
    fun `likePost inserts like correctly`() = runTest {
        val alice = CommentTestSeed.seedUser()
        val post = CommentTestSeed.seedPost(alice.userId)

        dao.likePost(alice.userId, post.postId)

        assertTrue(LikeTestSeed.likeExists(alice.userId, post.postId))
    }

    @Test
    fun `likePost throws ExposedSQLException with FK violation when postId does not exist`() {
        val alice = CommentTestSeed.seedUser()
        val ghostPostId = UUID.randomUUID()

        val ex = assertThrows(ExposedSQLException::class.java) {
            runBlocking { dao.likePost(alice.userId, ghostPostId) }
        }
        // SQLState 23503 = foreign_key_violation în PostgreSQL
        assertEquals("23503", ex.sqlState)
    }

    @Test
    fun `likePost throws ExposedSQLException with unique violation on duplicate`() {
        val alice = CommentTestSeed.seedUser()
        val post = CommentTestSeed.seedPost(alice.userId)

        runBlocking { dao.likePost(alice.userId, post.postId) }

        val ex = assertThrows(ExposedSQLException::class.java) {
            runBlocking { dao.likePost(alice.userId, post.postId) }
        }
        // SQLState 23505 = unique_violation în PostgreSQL
        assertEquals("23505", ex.sqlState)
    }

    // ---------- unlikePost ----------

    @Test
    fun `unlikePost removes like and returns 1`() = runTest {
        val alice = CommentTestSeed.seedUser()
        val post = CommentTestSeed.seedPost(alice.userId)
        LikeTestSeed.insertLike(alice.userId, post.postId)

        val rows = dao.unlikePost(alice.userId, post.postId)

        assertEquals(1, rows)
        assertFalse(LikeTestSeed.likeExists(alice.userId, post.postId))
    }

    @Test
    fun `unlikePost returns 0 when like does not exist`() = runTest {
        val alice = CommentTestSeed.seedUser()
        val post = CommentTestSeed.seedPost(alice.userId)

        val rows = dao.unlikePost(alice.userId, post.postId)

        assertEquals(0, rows)
    }

    @Test
    fun `unlikePost does not remove likes from other users`() = runTest {
        val alice = CommentTestSeed.seedUser(username = "alice")
        val bob = CommentTestSeed.seedUser(username = "bob", email = "bob@example.com")
        val post = CommentTestSeed.seedPost(alice.userId)
        LikeTestSeed.insertLike(alice.userId, post.postId)
        LikeTestSeed.insertLike(bob.userId, post.postId)

        dao.unlikePost(alice.userId, post.postId)

        assertFalse(LikeTestSeed.likeExists(alice.userId, post.postId))
        assertTrue(LikeTestSeed.likeExists(bob.userId, post.postId))
    }

    // ---------- hasUserLikedPost ----------

    @Test
    fun `hasUserLikedPost returns false when user has not liked post`() = runTest {
        val alice = CommentTestSeed.seedUser()
        val post = CommentTestSeed.seedPost(alice.userId)

        assertFalse(dao.hasUserLikedPost(alice.userId, post.postId))
    }

    @Test
    fun `hasUserLikedPost returns true after like`() = runTest {
        val alice = CommentTestSeed.seedUser()
        val post = CommentTestSeed.seedPost(alice.userId)
        LikeTestSeed.insertLike(alice.userId, post.postId)

        assertTrue(dao.hasUserLikedPost(alice.userId, post.postId))
    }

    @Test
    fun `hasUserLikedPost returns false after unlike`() = runTest {
        val alice = CommentTestSeed.seedUser()
        val post = CommentTestSeed.seedPost(alice.userId)
        LikeTestSeed.insertLike(alice.userId, post.postId)

        dao.unlikePost(alice.userId, post.postId)

        assertFalse(dao.hasUserLikedPost(alice.userId, post.postId))
    }

    @Test
    fun `hasUserLikedPost is scoped per user — does not bleed across users`() = runTest {
        val alice = CommentTestSeed.seedUser(username = "alice")
        val bob = CommentTestSeed.seedUser(username = "bob", email = "bob@example.com")
        val post = CommentTestSeed.seedPost(alice.userId)
        LikeTestSeed.insertLike(alice.userId, post.postId)

        assertTrue(dao.hasUserLikedPost(alice.userId, post.postId))
        assertFalse(dao.hasUserLikedPost(bob.userId, post.postId))
    }

    @Test
    fun `hasUserLikedPost is scoped per post — does not bleed across posts`() = runTest {
        val alice = CommentTestSeed.seedUser()
        val postA = CommentTestSeed.seedPost(alice.userId)
        val postB = CommentTestSeed.seedPost(alice.userId)
        LikeTestSeed.insertLike(alice.userId, postA.postId)

        assertTrue(dao.hasUserLikedPost(alice.userId, postA.postId))
        assertFalse(dao.hasUserLikedPost(alice.userId, postB.postId))
    }

    // ---------- getLikeCount ----------

    @Test
    fun `getLikeCount returns 0 for post without likes`() = runTest {
        val alice = CommentTestSeed.seedUser()
        val post = CommentTestSeed.seedPost(alice.userId)

        assertEquals(0L, dao.getLikeCount(post.postId))
    }

    @Test
    fun `getLikeCount returns 0 for non-existent postId`() = runTest {
        assertEquals(0L, dao.getLikeCount(UUID.randomUUID()))
    }

    @Test
    fun `getLikeCount returns correct count after multiple likes`() = runTest {
        val alice = CommentTestSeed.seedUser(username = "alice")
        val bob = CommentTestSeed.seedUser(username = "bob", email = "bob@example.com")
        val carol = CommentTestSeed.seedUser(username = "carol", email = "carol@example.com")
        val post = CommentTestSeed.seedPost(alice.userId)

        LikeTestSeed.insertLike(alice.userId, post.postId)
        LikeTestSeed.insertLike(bob.userId, post.postId)
        LikeTestSeed.insertLike(carol.userId, post.postId)

        assertEquals(3L, dao.getLikeCount(post.postId))
    }

    @Test
    fun `getLikeCount decrements after unlike`() = runTest {
        val alice = CommentTestSeed.seedUser(username = "alice")
        val bob = CommentTestSeed.seedUser(username = "bob", email = "bob@example.com")
        val post = CommentTestSeed.seedPost(alice.userId)
        LikeTestSeed.insertLike(alice.userId, post.postId)
        LikeTestSeed.insertLike(bob.userId, post.postId)

        dao.unlikePost(alice.userId, post.postId)

        assertEquals(1L, dao.getLikeCount(post.postId))
    }

    @Test
    fun `getLikeCount is scoped per post — counts are independent`() = runTest {
        val alice = CommentTestSeed.seedUser(username = "alice")
        val bob = CommentTestSeed.seedUser(username = "bob", email = "bob@example.com")
        val postA = CommentTestSeed.seedPost(alice.userId)
        val postB = CommentTestSeed.seedPost(alice.userId)

        LikeTestSeed.insertLike(alice.userId, postA.postId)
        LikeTestSeed.insertLike(alice.userId, postB.postId)
        LikeTestSeed.insertLike(bob.userId, postB.postId)

        assertEquals(1L, dao.getLikeCount(postA.postId))
        assertEquals(2L, dao.getLikeCount(postB.postId))
    }

    // ---------- cascade ----------

    @Test
    fun `cascade deleting post removes its likes`() = runTest {
        val alice = CommentTestSeed.seedUser()
        val post = CommentTestSeed.seedPost(alice.userId)
        LikeTestSeed.insertLike(alice.userId, post.postId)

        transaction {
            com.carspotter.features.post.PostTable.deleteWhere {
                com.carspotter.features.post.PostTable.id eq post.postId
            }
        }

        assertEquals(0L, LikeTestSeed.countLikes(post.postId))
    }

    @Test
    fun `cascade deleting user removes their likes`() = runTest {
        val alice = CommentTestSeed.seedUser()
        val post = CommentTestSeed.seedPost(alice.userId)
        LikeTestSeed.insertLike(alice.userId, post.postId)

        // Ștergerea user-ului cascadează în users_cars, posts, likes etc.
        transaction {
            com.carspotter.features.user.UserTable.deleteWhere {
                com.carspotter.features.user.UserTable.id eq alice.userId
            }
        }

        assertFalse(LikeTestSeed.likeExists(alice.userId, post.postId))
    }
}