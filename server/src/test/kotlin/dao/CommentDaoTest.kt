package dao

import features.comment.CommentDAO
import com.carspotter.features.comment.CommentTable
import com.carspotter.features.post.PostTable
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import testutils.CommentTestSeed
import testutils.TestDatabaseFactory
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CommentDaoTest {

    private val dao = CommentDAO()

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

    // ---------- addComment ----------

    @Test
    fun `addComment inserts and returns DTO with username from join`() = runTest {
        val alice = CommentTestSeed.seedUser(username = "alice", profilePicturePath = "/uploads/alice.jpg")
        val post = CommentTestSeed.seedPost(alice.userId)

        val created = dao.addComment(alice.userId, post.postId, "Nice ride!")

        assertNotNull(created.id)
        assertEquals(alice.userId, created.userId)
        assertEquals(post.postId, created.postId)
        assertEquals("Nice ride!", created.commentText)
        assertEquals("alice", created.username)
        assertEquals("/uploads/alice.jpg", created.profilePicturePath)
        assertNotNull(created.createdAt)
    }

    @Test
    fun `addComment FK violation when postId does not exist`() = runTest {
        val alice = CommentTestSeed.seedUser(username = "alice")
        val ghostPostId = UUID.randomUUID()

        assertThrows(ExposedSQLException::class.java) {
            kotlinx.coroutines.runBlocking {
                dao.addComment(alice.userId, ghostPostId, "any")
            }
        }
    }

    // ---------- getCommentsForPost ----------

    @Test
    fun `getCommentsForPost returns empty list when post has no comments`() = runTest {
        val alice = CommentTestSeed.seedUser()
        val post = CommentTestSeed.seedPost(alice.userId)

        val comments = dao.getCommentsForPost(post.postId)

        assertTrue(comments.isEmpty())
    }

    @Test
    fun `getCommentsForPost returns empty list for non-existent post`() = runTest {
        val comments = dao.getCommentsForPost(UUID.randomUUID())
        assertTrue(comments.isEmpty())
    }

    @Test
    fun `getCommentsForPost returns comments ordered by createdAt ASC`() = runTest {
        val alice = CommentTestSeed.seedUser(username = "alice")
        val bob = CommentTestSeed.seedUser(username = "bob", email = "bob@example.com")
        val post = CommentTestSeed.seedPost(alice.userId)

        // Inserăm intenționat în ordine ne-cronologică logic. Asigurăm progresia createdAt
        // prin sleep-uri scurte între inserts (timestamp default e CURRENT_TIMESTAMP).
        dao.addComment(alice.userId, post.postId, "first")
        Thread.sleep(15)
        dao.addComment(bob.userId, post.postId, "second")
        Thread.sleep(15)
        dao.addComment(alice.userId, post.postId, "third")

        val comments = dao.getCommentsForPost(post.postId)

        assertEquals(listOf("first", "second", "third"), comments.map { it.commentText })
        // Verificăm că createdAt este monoton crescător
        val timestamps = comments.map { it.createdAt }
        assertTrue(timestamps[0] <= timestamps[1])
        assertTrue(timestamps[1] <= timestamps[2])
    }

    @Test
    fun `getCommentsForPost includes username and avatar from joined user`() = runTest {
        val alice = CommentTestSeed.seedUser(username = "alice", profilePicturePath = "/uploads/alice.jpg")
        val bob = CommentTestSeed.seedUser(username = "bob", email = "bob@example.com", profilePicturePath = null)
        val post = CommentTestSeed.seedPost(alice.userId)

        CommentTestSeed.insertComment(alice.userId, post.postId, "by alice")
        CommentTestSeed.insertComment(bob.userId, post.postId, "by bob")

        val comments = dao.getCommentsForPost(post.postId)

        val byAlice = comments.first { it.commentText == "by alice" }
        val byBob = comments.first { it.commentText == "by bob" }

        assertEquals("alice", byAlice.username)
        assertEquals("/uploads/alice.jpg", byAlice.profilePicturePath)
        assertEquals("bob", byBob.username)
        assertNull(byBob.profilePicturePath)
    }

    @Test
    fun `getCommentsForPost does not return comments from other posts`() = runTest {
        val alice = CommentTestSeed.seedUser()
        val postA = CommentTestSeed.seedPost(alice.userId)
        val postB = CommentTestSeed.seedPost(alice.userId)

        CommentTestSeed.insertComment(alice.userId, postA.postId, "on A")
        CommentTestSeed.insertComment(alice.userId, postB.postId, "on B")

        val onA = dao.getCommentsForPost(postA.postId)

        assertEquals(1, onA.size)
        assertEquals("on A", onA.single().commentText)
    }

    // ---------- getCommentById ----------

    @Test
    fun `getCommentById returns null for unknown id`() = runTest {
        val fetched = dao.getCommentById(UUID.randomUUID())
        assertNull(fetched)
    }

    @Test
    fun `getCommentById returns comment with all fields populated including createdAt and username`() = runTest {
        val alice = CommentTestSeed.seedUser(username = "alice", profilePicturePath = "/uploads/alice.jpg")
        val post = CommentTestSeed.seedPost(alice.userId)
        val commentId = CommentTestSeed.insertComment(alice.userId, post.postId, "hi")

        val fetched = dao.getCommentById(commentId)

        // Regression: vechiul DAO uita să mapeze createdAt la getCommentById
        assertNotNull(fetched)
        assertEquals(commentId, fetched!!.id)
        assertEquals("hi", fetched.commentText)
        assertEquals("alice", fetched.username)
        assertEquals("/uploads/alice.jpg", fetched.profilePicturePath)
        assertNotNull(fetched.createdAt)
    }

    // ---------- deleteComment ----------

    @Test
    fun `deleteComment removes row and returns 1`() = runTest {
        val alice = CommentTestSeed.seedUser()
        val post = CommentTestSeed.seedPost(alice.userId)
        val commentId = CommentTestSeed.insertComment(alice.userId, post.postId, "to be deleted")

        val rows = dao.deleteComment(commentId)

        assertEquals(1, rows)
        assertNull(dao.getCommentById(commentId))
    }

    @Test
    fun `deleteComment returns 0 for non-existent id`() = runTest {
        val rows = dao.deleteComment(UUID.randomUUID())
        assertEquals(0, rows)
    }

    // ---------- cascade behavior ----------

    @Test
    fun `cascade deleting post removes its comments`() = runTest {
        val alice = CommentTestSeed.seedUser()
        val post = CommentTestSeed.seedPost(alice.userId)
        CommentTestSeed.insertComment(alice.userId, post.postId, "c1")
        CommentTestSeed.insertComment(alice.userId, post.postId, "c2")

        // Ștergem postul direct prin Exposed pentru a testa cascade-ul DB
        transaction {
            PostTable.deleteWhere { id eq post.postId }
        }

        val remaining = transaction {
            CommentTable.selectAll().where { CommentTable.postId eq post.postId }.count()
        }
        assertEquals(0L, remaining)
    }

    // ---------- DB constraints ----------

    @Test
    fun `cannot insert blank comment text due to DB check constraint`() = runTest {
        val alice = CommentTestSeed.seedUser()
        val post = CommentTestSeed.seedPost(alice.userId)

        // chk_comment_text_not_blank prevents whitespace-only inserts
        assertThrows(ExposedSQLException::class.java) {
            transaction {
                CommentTable.insert {
                    it[CommentTable.userId] = alice.userId
                    it[CommentTable.postId] = post.postId
                    it[CommentTable.commentText] = "   "
                }
            }
        }
    }

    @Test
    fun `cannot insert comment over 1000 chars due to V2 check constraint`() = runTest {
        val alice = CommentTestSeed.seedUser()
        val post = CommentTestSeed.seedPost(alice.userId)
        val tooLong = "a".repeat(1001)

        // chk_comment_text_max_length adăugat în V2__comments_max_length.sql
        val threw = try {
            transaction {
                CommentTable.insert {
                    it[CommentTable.userId] = alice.userId
                    it[CommentTable.postId] = post.postId
                    it[CommentTable.commentText] = tooLong
                }
            }
            false
        } catch (_: ExposedSQLException) {
            true
        }
        assertTrue(threw, "expected DB constraint to reject 1001-char comment text")
    }

    @Test
    fun `boundary 1000 chars is accepted`() = runTest {
        val alice = CommentTestSeed.seedUser()
        val post = CommentTestSeed.seedPost(alice.userId)
        val exact = "a".repeat(1000)

        val created = dao.addComment(alice.userId, post.postId, exact)
        assertEquals(1000, created.commentText.length)
    }
}