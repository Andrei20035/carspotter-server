package com.carspotter.features.scoring

import com.carspotter.features.post.PostTable
import com.carspotter.features.user.UserTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.UUID

interface IScoringDao {
    /**
     * Camera-post creation award: set posts.points = [points] AND spot_score += [points], atomically.
     * Only called when [points] > 0 (i.e. under the daily cap).
     */
    suspend fun applyCreationPoints(userId: UUID, postId: UUID, points: Int)

    /**
     * Engagement delta (like +1 / unlike -1 / first comment +5):
     * posts.points += delta AND spot_score += delta, both floored at 0, atomically.
     */
    suspend fun applyEngagementPoints(ownerId: UUID, postId: UUID, delta: Int)

    /**
     * Delete reversal: spot_score -= points (floored at 0) AND delete the post row, atomically.
     * Cascade removes associated likes and comments.
     * Returns the number of deleted post rows.
     */
    suspend fun reverseAndDeletePost(ownerId: UUID, postId: UUID, points: Int): Int
}

class ScoringDaoImpl : IScoringDao {

    override suspend fun applyCreationPoints(userId: UUID, postId: UUID, points: Int) = transaction {
        PostTable.update({ PostTable.id eq postId }) {
            it[PostTable.points] = points
        }
        val currentScore = UserTable
            .select(UserTable.spotScore)
            .where { UserTable.id eq userId }
            .singleOrNull()
            ?.get(UserTable.spotScore) ?: 0
        val newScore = maxOf(0, currentScore + points)
        UserTable.update({ UserTable.id eq userId }) {
            it[UserTable.spotScore] = newScore
        }
        Unit
    }

    override suspend fun applyEngagementPoints(ownerId: UUID, postId: UUID, delta: Int) = transaction {
        val currentPoints = PostTable
            .select(PostTable.points)
            .where { PostTable.id eq postId }
            .singleOrNull()
            ?.get(PostTable.points) ?: 0
        val newPoints = maxOf(0, currentPoints + delta)
        PostTable.update({ PostTable.id eq postId }) {
            it[PostTable.points] = newPoints
        }
        val currentScore = UserTable
            .select(UserTable.spotScore)
            .where { UserTable.id eq ownerId }
            .singleOrNull()
            ?.get(UserTable.spotScore) ?: 0
        val newScore = maxOf(0, currentScore + delta)
        UserTable.update({ UserTable.id eq ownerId }) {
            it[UserTable.spotScore] = newScore
        }
        Unit
    }

    override suspend fun reverseAndDeletePost(ownerId: UUID, postId: UUID, points: Int): Int = transaction {
        if (points > 0) {
            val currentScore = UserTable
                .select(UserTable.spotScore)
                .where { UserTable.id eq ownerId }
                .singleOrNull()
                ?.get(UserTable.spotScore) ?: 0
            val newScore = maxOf(0, currentScore - points)
            UserTable.update({ UserTable.id eq ownerId }) {
                it[UserTable.spotScore] = newScore
            }
        }
        PostTable.deleteWhere { id eq postId }
    }
}
