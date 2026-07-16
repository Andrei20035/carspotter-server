package dao

import com.revio.server.features.activity.ActivityEventType
import features.activity.ActivityDAO
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import testutils.CommentTestSeed
import testutils.LikeTestSeed
import testutils.TestDatabaseFactory
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ActivityDaoTest {

    private val dao = ActivityDAO()

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

    // ---------- recordEventIdempotent ----------

    @Test
    fun `recordEventIdempotent inserts a new event and returns true`() = runTest {
        val alice = CommentTestSeed.seedUser()
        val today = LocalDate.now()

        val inserted = dao.recordEventIdempotent(alice.userId, ActivityEventType.STREAK, today, 5)

        assertTrue(inserted)
        val events = dao.getPersistedEvents(alice.userId, 10)
        assertEquals(1, events.size)
        assertEquals(ActivityEventType.STREAK, events.single().type)
        assertEquals(5, events.single().valueInt)
    }

    @Test
    fun `recordEventIdempotent does not duplicate on same user, type and date`() = runTest {
        val alice = CommentTestSeed.seedUser()
        val today = LocalDate.now()

        val first = dao.recordEventIdempotent(alice.userId, ActivityEventType.STREAK, today, 5)
        val second = dao.recordEventIdempotent(alice.userId, ActivityEventType.STREAK, today, 6)

        assertTrue(first)
        assertFalse(second)
        val events = dao.getPersistedEvents(alice.userId, 10)
        assertEquals(1, events.size)
        // Original value preserved — the second (duplicate) insert was ignored.
        assertEquals(5, events.single().valueInt)
    }

    @Test
    fun `recordEventIdempotent allows same type on different dates`() = runTest {
        val alice = CommentTestSeed.seedUser()
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)

        dao.recordEventIdempotent(alice.userId, ActivityEventType.STREAK, yesterday, 4)
        dao.recordEventIdempotent(alice.userId, ActivityEventType.STREAK, today, 5)

        val events = dao.getPersistedEvents(alice.userId, 10)
        assertEquals(2, events.size)
    }

    @Test
    fun `recordEventIdempotent allows different types on same date`() = runTest {
        val alice = CommentTestSeed.seedUser()
        val today = LocalDate.now()

        dao.recordEventIdempotent(alice.userId, ActivityEventType.STREAK, today, 5)
        dao.recordEventIdempotent(alice.userId, ActivityEventType.LEADERBOARD_UP, today, 3)

        val events = dao.getPersistedEvents(alice.userId, 10)
        assertEquals(2, events.size)
    }

    @Test
    fun `getPersistedEvents returns newest first`() = runTest {
        val alice = CommentTestSeed.seedUser()
        dao.recordEventIdempotent(alice.userId, ActivityEventType.STREAK, LocalDate.now().minusDays(2), 3)
        dao.recordEventIdempotent(alice.userId, ActivityEventType.LEADERBOARD_UP, LocalDate.now().minusDays(1), 2)
        dao.recordEventIdempotent(alice.userId, ActivityEventType.STREAK, LocalDate.now(), 4)

        val events = dao.getPersistedEvents(alice.userId, 10)

        assertEquals(listOf(4, 2, 3), events.map { it.valueInt })
    }

    @Test
    fun `getPersistedEvents does not leak events from other users`() = runTest {
        val alice = CommentTestSeed.seedUser("alice")
        val bob = CommentTestSeed.seedUser("bob")
        dao.recordEventIdempotent(alice.userId, ActivityEventType.STREAK, LocalDate.now(), 5)
        dao.recordEventIdempotent(bob.userId, ActivityEventType.STREAK, LocalDate.now(), 9)

        val aliceEvents = dao.getPersistedEvents(alice.userId, 10)

        assertEquals(1, aliceEvents.size)
        assertEquals(5, aliceEvents.single().valueInt)
    }

    // ---------- getLikeItems ----------

    @Test
    fun `getLikeItems returns likes received on owner posts with actor and post info`() = runTest {
        val owner = CommentTestSeed.seedUser("owner")
        val liker = CommentTestSeed.seedUser("liker")
        val post = CommentTestSeed.seedPost(owner.userId, customBrand = "Porsche", customModel = "GT3")
        LikeTestSeed.insertLike(liker.userId, post.postId)

        val items = dao.getLikeItems(owner.userId, 10)

        assertEquals(1, items.size)
        val item = items.single()
        assertEquals(liker.userId, item.actorUserId)
        assertEquals("liker", item.actorUsername)
        assertEquals(post.postId, item.postId)
        assertEquals("Porsche", item.brand)
        assertEquals("GT3", item.model)
    }

    @Test
    fun `getLikeItems excludes self-likes`() = runTest {
        val owner = CommentTestSeed.seedUser("owner")
        val post = CommentTestSeed.seedPost(owner.userId)
        LikeTestSeed.insertLike(owner.userId, post.postId)

        val items = dao.getLikeItems(owner.userId, 10)

        assertTrue(items.isEmpty())
    }

    @Test
    fun `getLikeItems only returns likes on the owner's own posts`() = runTest {
        val owner = CommentTestSeed.seedUser("owner")
        val otherOwner = CommentTestSeed.seedUser("otherOwner")
        val liker = CommentTestSeed.seedUser("liker")
        val otherPost = CommentTestSeed.seedPost(otherOwner.userId)
        LikeTestSeed.insertLike(liker.userId, otherPost.postId)

        val items = dao.getLikeItems(owner.userId, 10)

        assertTrue(items.isEmpty())
    }

    @Test
    fun `getLikeItems returns newest first`() = runTest {
        val owner = CommentTestSeed.seedUser("owner")
        val likerA = CommentTestSeed.seedUser("likerA")
        val likerB = CommentTestSeed.seedUser("likerB")
        val post = CommentTestSeed.seedPost(owner.userId)
        LikeTestSeed.insertLike(likerA.userId, post.postId)
        LikeTestSeed.insertLike(likerB.userId, post.postId)

        val items = dao.getLikeItems(owner.userId, 10)

        assertEquals(2, items.size)
        assertTrue(items[0].createdAt >= items[1].createdAt)
    }

    // ---------- getCommentItems ----------

    @Test
    fun `getCommentItems returns comments received on owner posts with actor, post info and text`() = runTest {
        val owner = CommentTestSeed.seedUser("owner")
        val commenter = CommentTestSeed.seedUser("commenter")
        val post = CommentTestSeed.seedPost(owner.userId, customBrand = "BMW", customModel = "M4")
        CommentTestSeed.insertComment(commenter.userId, post.postId, "Incredible spec, where did you find this?")

        val items = dao.getCommentItems(owner.userId, 10)

        assertEquals(1, items.size)
        val item = items.single()
        assertEquals(commenter.userId, item.actorUserId)
        assertEquals("commenter", item.actorUsername)
        assertEquals(post.postId, item.postId)
        assertEquals("BMW", item.brand)
        assertEquals("M4", item.model)
        assertEquals("Incredible spec, where did you find this?", item.commentText)
    }

    @Test
    fun `getCommentItems excludes self-comments`() = runTest {
        val owner = CommentTestSeed.seedUser("owner")
        val post = CommentTestSeed.seedPost(owner.userId)
        CommentTestSeed.insertComment(owner.userId, post.postId, "nice one")

        val items = dao.getCommentItems(owner.userId, 10)

        assertTrue(items.isEmpty())
    }

    // ---------- countTodayUniqueInteractors ----------

    @Test
    fun `countTodayUniqueInteractors counts distinct users across likes and comments`() = runTest {
        val owner = CommentTestSeed.seedUser("owner")
        val actorA = CommentTestSeed.seedUser("actorA")
        val actorB = CommentTestSeed.seedUser("actorB")
        val post = CommentTestSeed.seedPost(owner.userId)
        LikeTestSeed.insertLike(actorA.userId, post.postId)
        CommentTestSeed.insertComment(actorA.userId, post.postId, "hi")
        CommentTestSeed.insertComment(actorB.userId, post.postId, "hey")

        val count = dao.countTodayUniqueInteractors(owner.userId, Instant.now().minus(1, ChronoUnit.DAYS))

        // actorA liked AND commented -> counted once; actorB commented -> counted once.
        assertEquals(2L, count)
    }

    @Test
    fun `countTodayUniqueInteractors excludes self-interactions`() = runTest {
        val owner = CommentTestSeed.seedUser("owner")
        val post = CommentTestSeed.seedPost(owner.userId)
        LikeTestSeed.insertLike(owner.userId, post.postId)
        CommentTestSeed.insertComment(owner.userId, post.postId, "my own post")

        val count = dao.countTodayUniqueInteractors(owner.userId, Instant.now().minus(1, ChronoUnit.DAYS))

        assertEquals(0L, count)
    }

    @Test
    fun `countTodayUniqueInteractors excludes interactions before dayStart`() = runTest {
        val owner = CommentTestSeed.seedUser("owner")
        val actor = CommentTestSeed.seedUser("actor")
        val post = CommentTestSeed.seedPost(owner.userId)
        LikeTestSeed.insertLike(actor.userId, post.postId)

        // dayStart set in the future relative to the just-inserted like.
        val count = dao.countTodayUniqueInteractors(owner.userId, Instant.now().plus(1, ChronoUnit.DAYS))

        assertEquals(0L, count)
    }
}
