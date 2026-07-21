package features.like

import com.revio.server.features.like.LikeTable
import com.revio.server.features.post.PostTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

interface ILikeDAO {
    suspend fun likePost(userId: UUID, postId: UUID)
    suspend fun unlikePost(userId: UUID, postId: UUID): Int
    suspend fun hasUserLikedPost(userId: UUID, postId: UUID): Boolean
    suspend fun getLikeCount(postId: UUID): Long

    /** Batched like counts for a set of posts. Returns a map postId -> count (posts with no likes are absent). */
    suspend fun getLikeCountsForPosts(postIds: List<UUID>): Map<UUID, Long>

    /** Subset of the given postIds that the user has liked. */
    suspend fun getLikedPostIds(userId: UUID, postIds: List<UUID>): Set<UUID>

    /** Total likes received across all of this user's posts. */
    suspend fun getLikesReceivedByUser(userId: UUID): Long
}

class LikeDAO : ILikeDAO {

    override suspend fun likePost(userId: UUID, postId: UUID): Unit = transaction {
        LikeTable.insert {
            it[LikeTable.userId] = userId
            it[LikeTable.postId] = postId
        }
        Unit
    }

    override suspend fun unlikePost(userId: UUID, postId: UUID): Int = transaction {
        LikeTable.deleteWhere {
            (LikeTable.userId eq userId) and (LikeTable.postId eq postId)
        }
    }

    override suspend fun hasUserLikedPost(userId: UUID, postId: UUID): Boolean = transaction {
        LikeTable
            .select(LikeTable.id)
            .where { (LikeTable.userId eq userId) and (LikeTable.postId eq postId) }
            .limit(1)
            .any()
    }

    override suspend fun getLikeCount(postId: UUID): Long = transaction {
        LikeTable
            .select(LikeTable.id)
            .where { LikeTable.postId eq postId }
            .count()
    }

    override suspend fun getLikeCountsForPosts(postIds: List<UUID>): Map<UUID, Long> = transaction {
        if (postIds.isEmpty()) return@transaction emptyMap()
        val countExpr = LikeTable.id.count()
        LikeTable
            .select(LikeTable.postId, countExpr)
            .where { LikeTable.postId inList postIds }
            .groupBy(LikeTable.postId)
            .associate { it[LikeTable.postId] to it[countExpr] }
    }

    override suspend fun getLikedPostIds(userId: UUID, postIds: List<UUID>): Set<UUID> = transaction {
        if (postIds.isEmpty()) return@transaction emptySet()
        LikeTable
            .select(LikeTable.postId)
            .where { (LikeTable.userId eq userId) and (LikeTable.postId inList postIds) }
            .map { it[LikeTable.postId] }
            .toSet()
    }

    override suspend fun getLikesReceivedByUser(userId: UUID): Long = transaction {
        LikeTable
            .innerJoin(PostTable, { LikeTable.postId }, { PostTable.id })
            .select(LikeTable.id)
            .where { PostTable.userId eq userId }
            .count()
    }
}