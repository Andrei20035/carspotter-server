package com.revio.server.features.activity

import com.revio.server.core.storage.IStorageService
import com.revio.server.core.util.resolveZone
import com.revio.server.features.activity.dto.ActivityItemDTO
import com.revio.server.features.activity.dto.ActivityResponseDTO
import com.revio.server.features.leaderboard.ILeaderboardDAO
import com.revio.server.features.leaderboard.ILeaderboardSnapshotDAO
import com.revio.server.features.post.IPostDAO
import features.activity.ActivityEventRow
import features.activity.CommentActivityRow
import features.activity.IActivityDAO
import features.activity.LikeActivityRow
import java.time.DayOfWeek
import java.time.Instant
import java.time.temporal.TemporalAdjusters
import java.util.UUID

interface IActivityService {
    /**
     * Aggregates a user's activity feed: weekly SpotScore delta, today's unique interactor
     * count, and the merged/sorted timeline of LIKE, COMMENT, LEADERBOARD_UP and STREAK items.
     */
    suspend fun getActivity(userId: UUID, limit: Int, timezone: String?): ActivityResponseDTO
}

class ActivityService(
    private val activityDao: IActivityDAO,
    private val snapshotDao: ILeaderboardSnapshotDAO,
    private val leaderboardDao: ILeaderboardDAO,
    private val postDao: IPostDAO,
    private val storageService: IStorageService,
    private val snapshotZoneId: String? = System.getenv("LEADERBOARD_SNAPSHOT_ZONE"),
) : IActivityService {

    override suspend fun getActivity(userId: UUID, limit: Int, timezone: String?): ActivityResponseDTO {
        val zone = resolveZone(timezone ?: snapshotZoneId)
        val today = Instant.now().atZone(zone).toLocalDate()
        val dayStart = today.atStartOfDay(zone).toInstant()
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        val currentScore = leaderboardDao.getUserScoreAndStreak(userId)?.spotScore ?: 0
        val weekStartBaseline = snapshotDao.getSpotScoreOnOrBefore(userId, weekStart)
        val weeklySpotScore = if (weekStartBaseline != null) {
            (currentScore - weekStartBaseline).coerceAtLeast(0)
        } else {
            // No snapshot yet for this week's Monday (e.g. cron hasn't run) — fall back to
            // summing points earned since then directly from posts.
            val weekStartInstant = weekStart.atStartOfDay(zone).toInstant()
            postDao.sumPointsSince(userId, weekStartInstant).coerceAtLeast(0)
        }

        val todayInteractions = activityDao.countTodayUniqueInteractors(userId, dayStart).toInt()

        val likeItems = activityDao.getLikeItems(userId, limit).map { it.toDTO() }
        val commentItems = activityDao.getCommentItems(userId, limit).map { it.toDTO() }
        val persistedItems = activityDao.getPersistedEvents(userId, limit).map { it.toDTO() }

        val items = (likeItems + commentItems + persistedItems)
            .sortedByDescending { it.createdAt }
            .take(limit)

        return ActivityResponseDTO(
            weeklySpotScore = weeklySpotScore,
            todayInteractions = todayInteractions,
            items = items,
        )
    }

    private fun LikeActivityRow.toDTO(): ActivityItemDTO = ActivityItemDTO(
        type = "LIKE",
        id = "like:$likeId",
        actorUserId = actorUserId,
        actorUsername = actorUsername,
        actorAvatarUrl = actorProfilePicturePath?.let(storageService::resolveUrl),
        postId = postId,
        postThumbnailUrl = storageService.resolveUrl(postImageKey),
        brand = brand,
        model = model,
        createdAt = createdAt,
    )

    private fun CommentActivityRow.toDTO(): ActivityItemDTO = ActivityItemDTO(
        type = "COMMENT",
        id = "comment:$commentId",
        actorUserId = actorUserId,
        actorUsername = actorUsername,
        actorAvatarUrl = actorProfilePicturePath?.let(storageService::resolveUrl),
        postId = postId,
        postThumbnailUrl = storageService.resolveUrl(postImageKey),
        brand = brand,
        model = model,
        commentText = commentText,
        createdAt = createdAt,
    )

    private fun ActivityEventRow.toDTO(): ActivityItemDTO = when (type) {
        ActivityEventType.STREAK -> ActivityItemDTO(
            type = "STREAK",
            id = "streak:$id",
            streakDays = valueInt,
            createdAt = createdAt,
        )
        ActivityEventType.LEADERBOARD_UP -> ActivityItemDTO(
            type = "LEADERBOARD_UP",
            id = "lb:$id",
            placesMoved = valueInt,
            createdAt = createdAt,
        )
    }
}
