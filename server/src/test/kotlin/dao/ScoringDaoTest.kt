package dao

import com.carspotter.features.post.PostSource
import com.carspotter.features.post.PostTable
import com.carspotter.features.scoring.ScoringDaoImpl
import com.carspotter.features.user.UserTable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import testutils.CommentTestSeed
import testutils.TestDatabaseFactory
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScoringDaoTest {

    private val dao = ScoringDaoImpl()

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

    // ---------- helpers ----------

    private fun spotScore(userId: UUID): Int = transaction {
        UserTable.select(UserTable.spotScore)
            .where { UserTable.id eq userId }
            .singleOrNull()?.get(UserTable.spotScore) ?: 0
    }

    private fun postPoints(postId: UUID): Int? = transaction {
        PostTable.select(PostTable.points)
            .where { PostTable.id eq postId }
            .singleOrNull()?.get(PostTable.points)
    }

    private fun postExists(postId: UUID): Boolean = transaction {
        PostTable.select(PostTable.id)
            .where { PostTable.id eq postId }
            .any()
    }

    private fun setSpotScore(userId: UUID, score: Int) = transaction {
        UserTable.update({ UserTable.id eq userId }) { it[UserTable.spotScore] = score }
    }

    private fun seedCameraPost(ownerUserId: UUID): UUID = transaction {
        PostTable.insert {
            it[PostTable.userId] = ownerUserId
            it[PostTable.imageKey] = "posts/test.jpg"
            it[PostTable.customBrand] = "bmw"
            it[PostTable.customModel] = "m3"
            it[PostTable.postSource] = PostSource.CAMERA.name
        }[PostTable.id].value
    }

    // ---------- applyCreationPoints ----------

    @Test
    fun `applyCreationPoints sets post points and increments spot_score atomically`() = runTest {
        val user = CommentTestSeed.seedUser()
        val postId = seedCameraPost(user.userId)

        dao.applyCreationPoints(user.userId, postId, 10)

        assertEquals(10, postPoints(postId))
        assertEquals(10, spotScore(user.userId))
    }

    @Test
    fun `applyCreationPoints accumulates on top of existing spot_score`() = runTest {
        val user = CommentTestSeed.seedUser()
        val postId = seedCameraPost(user.userId)
        setSpotScore(user.userId, 5)

        dao.applyCreationPoints(user.userId, postId, 10)

        assertEquals(10, postPoints(postId))
        assertEquals(15, spotScore(user.userId))
    }

    // ---------- applyEngagementPoints ----------

    @Test
    fun `applyEngagementPoints increments both post points and spot_score`() = runTest {
        val owner = CommentTestSeed.seedUser(username = "owner")
        val postId = seedCameraPost(owner.userId)

        dao.applyEngagementPoints(owner.userId, postId, 1)

        assertEquals(1, postPoints(postId))
        assertEquals(1, spotScore(owner.userId))
    }

    @Test
    fun `applyEngagementPoints decrements both post points and spot_score`() = runTest {
        val owner = CommentTestSeed.seedUser(username = "owner")
        val postId = seedCameraPost(owner.userId)
        // Pre-seed: post has 5 points, owner has 5 spot_score
        dao.applyEngagementPoints(owner.userId, postId, 5)

        dao.applyEngagementPoints(owner.userId, postId, -1)

        assertEquals(4, postPoints(postId))
        assertEquals(4, spotScore(owner.userId))
    }

    @Test
    fun `applyEngagementPoints floors post points at 0`() = runTest {
        val owner = CommentTestSeed.seedUser(username = "owner")
        val postId = seedCameraPost(owner.userId)
        // post.points starts at 0, delta = -1 → floor at 0

        dao.applyEngagementPoints(owner.userId, postId, -1)

        assertEquals(0, postPoints(postId))
        assertEquals(0, spotScore(owner.userId))
    }

    @Test
    fun `applyEngagementPoints first-comment adds 5 to both`() = runTest {
        val owner = CommentTestSeed.seedUser(username = "owner")
        val postId = seedCameraPost(owner.userId)

        dao.applyEngagementPoints(owner.userId, postId, 5)

        assertEquals(5, postPoints(postId))
        assertEquals(5, spotScore(owner.userId))
    }

    // ---------- reverseAndDeletePost ----------

    @Test
    fun `reverseAndDeletePost subtracts points from spot_score and deletes post`() = runTest {
        val owner = CommentTestSeed.seedUser(username = "owner")
        val postId = seedCameraPost(owner.userId)
        setSpotScore(owner.userId, 15)

        val deleted = dao.reverseAndDeletePost(owner.userId, postId, 10)

        assertEquals(1, deleted)
        assertEquals(5, spotScore(owner.userId))
        assertNull(postPoints(postId))
    }

    @Test
    fun `reverseAndDeletePost floors spot_score at 0`() = runTest {
        val owner = CommentTestSeed.seedUser(username = "owner")
        val postId = seedCameraPost(owner.userId)
        setSpotScore(owner.userId, 3)

        dao.reverseAndDeletePost(owner.userId, postId, 10)

        assertEquals(0, spotScore(owner.userId))
    }

    @Test
    fun `reverseAndDeletePost with zero points only deletes post`() = runTest {
        val owner = CommentTestSeed.seedUser(username = "owner")
        val postId = seedCameraPost(owner.userId)
        setSpotScore(owner.userId, 7)

        val deleted = dao.reverseAndDeletePost(owner.userId, postId, 0)

        assertEquals(1, deleted)
        assertEquals(7, spotScore(owner.userId))
        assertNull(postPoints(postId))
    }

    @Test
    fun `reverseAndDeletePost returns 0 for non-existent post`() = runTest {
        val owner = CommentTestSeed.seedUser(username = "owner")
        val ghostId = UUID.randomUUID()

        val deleted = runBlocking { dao.reverseAndDeletePost(owner.userId, ghostId, 0) }

        assertEquals(0, deleted)
    }

    @Test
    fun `reverseAndDeletePost verifies post is removed from DB`() = runTest {
        val owner = CommentTestSeed.seedUser(username = "owner")
        val postId = seedCameraPost(owner.userId)

        dao.reverseAndDeletePost(owner.userId, postId, 0)

        assertEquals(false, postExists(postId))
    }
}
