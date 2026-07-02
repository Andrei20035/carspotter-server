package features.activity

import com.carspotter.features.activity.ActivityEventTable
import com.carspotter.features.activity.ActivityEventType
import com.carspotter.features.car_model.CarModelTable
import com.carspotter.features.comment.CommentTable
import com.carspotter.features.like.LikeTable
import com.carspotter.features.post.PostTable
import com.carspotter.features.user.UserTable
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertIgnoreAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class ActivityEventRow(
    val id: UUID,
    val type: ActivityEventType,
    val valueInt: Int,
    val createdAt: Instant,
)

data class LikeActivityRow(
    val likeId: UUID,
    val actorUserId: UUID,
    val actorUsername: String,
    val actorProfilePicturePath: String?,
    val postId: UUID,
    val postImageKey: String,
    val brand: String,
    val model: String,
    val createdAt: Instant,
)

data class CommentActivityRow(
    val commentId: UUID,
    val actorUserId: UUID,
    val actorUsername: String,
    val actorProfilePicturePath: String?,
    val postId: UUID,
    val postImageKey: String,
    val brand: String,
    val model: String,
    val commentText: String,
    val createdAt: Instant,
)

interface IActivityDAO {
    /**
     * Persists a milestone event for [userId] on [eventDate], unless one of the same
     * (userId, type, eventDate) already exists.
     * @return true if a new row was inserted, false if it already existed (idempotent no-op).
     */
    suspend fun recordEventIdempotent(userId: UUID, type: ActivityEventType, eventDate: LocalDate, valueInt: Int): Boolean

    /** Most recent persisted STREAK / LEADERBOARD_UP events for [userId], newest first. */
    suspend fun getPersistedEvents(userId: UUID, limit: Int): List<ActivityEventRow>

    /** Likes received by [ownerUserId]'s posts, excluding self-likes, newest first. */
    suspend fun getLikeItems(ownerUserId: UUID, limit: Int): List<LikeActivityRow>

    /** Comments received on [ownerUserId]'s posts, excluding self-comments, newest first. */
    suspend fun getCommentItems(ownerUserId: UUID, limit: Int): List<CommentActivityRow>

    /**
     * Count of distinct users (excluding [ownerUserId] itself) who liked or commented on
     * [ownerUserId]'s posts at or after [dayStart].
     */
    suspend fun countTodayUniqueInteractors(ownerUserId: UUID, dayStart: Instant): Long
}

class ActivityDAO : IActivityDAO {

    override suspend fun recordEventIdempotent(
        userId: UUID,
        type: ActivityEventType,
        eventDate: LocalDate,
        valueInt: Int,
    ): Boolean = transaction {
        val insertedId = ActivityEventTable.insertIgnoreAndGetId {
            it[ActivityEventTable.userId] = userId
            it[ActivityEventTable.type] = type.name
            it[ActivityEventTable.eventDate] = eventDate
            it[ActivityEventTable.valueInt] = valueInt
        }
        insertedId != null
    }

    override suspend fun getPersistedEvents(userId: UUID, limit: Int): List<ActivityEventRow> = transaction {
        ActivityEventTable
            .select(
                ActivityEventTable.id,
                ActivityEventTable.type,
                ActivityEventTable.valueInt,
                ActivityEventTable.createdAt,
            )
            .where { ActivityEventTable.userId eq userId }
            .orderBy(ActivityEventTable.createdAt to SortOrder.DESC)
            .limit(limit)
            .map {
                ActivityEventRow(
                    id = it[ActivityEventTable.id].value,
                    type = ActivityEventType.valueOf(it[ActivityEventTable.type]),
                    valueInt = it[ActivityEventTable.valueInt],
                    createdAt = it[ActivityEventTable.createdAt],
                )
            }
    }

    /** Resolves brand/model the same way as [com.carspotter.features.post.toPost]: car model if linked, else custom fields. */
    private fun ResultRow.resolveBrand(): String =
        (if (this[PostTable.carModelId] != null) this[CarModelTable.brand] else this[PostTable.customBrand])
            ?: error("Post ${this[PostTable.id].value} is missing brand")

    private fun ResultRow.resolveModel(): String =
        (if (this[PostTable.carModelId] != null) this[CarModelTable.model] else this[PostTable.customModel])
            ?: error("Post ${this[PostTable.id].value} is missing model")

    override suspend fun getLikeItems(ownerUserId: UUID, limit: Int): List<LikeActivityRow> = transaction {
        val actor = UserTable.alias("actor")

        (LikeTable innerJoin PostTable)
            .join(actor, JoinType.INNER, additionalConstraint = { LikeTable.userId eq actor[UserTable.id] })
            .join(CarModelTable, JoinType.LEFT, additionalConstraint = { PostTable.carModelId eq CarModelTable.id })
            .select(
                LikeTable.id,
                LikeTable.userId,
                actor[UserTable.username],
                actor[UserTable.profilePicturePath],
                PostTable.id,
                PostTable.imageKey,
                PostTable.carModelId,
                CarModelTable.brand,
                CarModelTable.model,
                PostTable.customBrand,
                PostTable.customModel,
                LikeTable.createdAt,
            )
            .where { (PostTable.userId eq ownerUserId) and (LikeTable.userId neq ownerUserId) }
            .orderBy(LikeTable.createdAt to SortOrder.DESC)
            .limit(limit)
            .map { row ->
                LikeActivityRow(
                    likeId = row[LikeTable.id].value,
                    actorUserId = row[LikeTable.userId],
                    actorUsername = row[actor[UserTable.username]],
                    actorProfilePicturePath = row[actor[UserTable.profilePicturePath]],
                    postId = row[PostTable.id].value,
                    postImageKey = row[PostTable.imageKey],
                    brand = row.resolveBrand(),
                    model = row.resolveModel(),
                    createdAt = row[LikeTable.createdAt],
                )
            }
    }

    override suspend fun getCommentItems(ownerUserId: UUID, limit: Int): List<CommentActivityRow> = transaction {
        val actor = UserTable.alias("actor")

        (CommentTable innerJoin PostTable)
            .join(actor, JoinType.INNER, additionalConstraint = { CommentTable.userId eq actor[UserTable.id] })
            .join(CarModelTable, JoinType.LEFT, additionalConstraint = { PostTable.carModelId eq CarModelTable.id })
            .select(
                CommentTable.id,
                CommentTable.userId,
                actor[UserTable.username],
                actor[UserTable.profilePicturePath],
                PostTable.id,
                PostTable.imageKey,
                PostTable.carModelId,
                CarModelTable.brand,
                CarModelTable.model,
                PostTable.customBrand,
                PostTable.customModel,
                CommentTable.commentText,
                CommentTable.createdAt,
            )
            .where { (PostTable.userId eq ownerUserId) and (CommentTable.userId neq ownerUserId) }
            .orderBy(CommentTable.createdAt to SortOrder.DESC)
            .limit(limit)
            .map { row ->
                CommentActivityRow(
                    commentId = row[CommentTable.id].value,
                    actorUserId = row[CommentTable.userId],
                    actorUsername = row[actor[UserTable.username]],
                    actorProfilePicturePath = row[actor[UserTable.profilePicturePath]],
                    postId = row[PostTable.id].value,
                    postImageKey = row[PostTable.imageKey],
                    brand = row.resolveBrand(),
                    model = row.resolveModel(),
                    commentText = row[CommentTable.commentText],
                    createdAt = row[CommentTable.createdAt],
                )
            }
    }

    override suspend fun countTodayUniqueInteractors(ownerUserId: UUID, dayStart: Instant): Long = transaction {
        val likeActors = (LikeTable innerJoin PostTable)
            .select(LikeTable.userId)
            .where {
                (PostTable.userId eq ownerUserId) and
                    (LikeTable.userId neq ownerUserId) and
                    (LikeTable.createdAt greaterEq dayStart)
            }
            .map { it[LikeTable.userId] }

        val commentActors = (CommentTable innerJoin PostTable)
            .select(CommentTable.userId)
            .where {
                (PostTable.userId eq ownerUserId) and
                    (CommentTable.userId neq ownerUserId) and
                    (CommentTable.createdAt greaterEq dayStart)
            }
            .map { it[CommentTable.userId] }

        (likeActors + commentActors).distinct().size.toLong()
    }
}
