package features.like

import com.carspotter.features.like.LikeTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

interface ILikeDAO {
    suspend fun likePost(userId: UUID, postId: UUID)
    suspend fun unlikePost(userId: UUID, postId: UUID): Int
    suspend fun hasUserLikedPost(userId: UUID, postId: UUID): Boolean
    suspend fun getLikeCount(postId: UUID): Long
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
}