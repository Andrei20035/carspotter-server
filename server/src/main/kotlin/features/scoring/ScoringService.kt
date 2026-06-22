package com.carspotter.features.scoring

import com.carspotter.features.post.IPostDAO
import com.carspotter.features.post.PostSource
import com.carspotter.features.user.IUserDAO
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

interface IScoringService {
    /**
     * Award SpotScore and update streak for a newly created camera post.
     * Must be called inside the same transaction as the post insert.
     * No-ops silently when [source] is GALLERY.
     */
    suspend fun onPostCreated(
        userId: UUID,
        source: PostSource,
        createdAtUtc: Instant,
        createdAtTimezone: String?,
    )

    /**
     * Award +[LIKE_POINTS] to [postOwnerId] when [likerId] likes a post.
     * No-op on self-likes. Must be called in the same transaction as the like insert.
     */
    suspend fun onPostLiked(postOwnerId: UUID, likerId: UUID)

    /**
     * Remove +[LIKE_POINTS] from [postOwnerId] when [unlikerId] removes a like.
     * No-op on self-unlike. Must be called in the same transaction as the like delete.
     */
    suspend fun onPostUnliked(postOwnerId: UUID, unlikerId: UUID)

    /**
     * Award +[COMMENT_POINTS] to [postOwnerId] for the first comment by [commenterId] on a post.
     * Self-comments and repeat commenters award nothing. Must be called in the same transaction.
     */
    suspend fun onFirstCommentByUser(postOwnerId: UUID, commenterId: UUID)
}

class ScoringServiceImpl(
    private val userDao: IUserDAO,
    private val postDao: IPostDAO,
) : IScoringService {

    companion object {
        const val CAMERA_POINTS = 10
        const val DAILY_CAMERA_CAP = 10
        const val LIKE_POINTS = 1
        const val COMMENT_POINTS = 5

        private val logger = LoggerFactory.getLogger(ScoringServiceImpl::class.java)
    }

    override suspend fun onPostCreated(
        userId: UUID,
        source: PostSource,
        createdAtUtc: Instant,
        createdAtTimezone: String?,
    ) {
        if (source != PostSource.CAMERA) return

        val zone = resolveZone(createdAtTimezone)
        val localDay: LocalDate = createdAtUtc.atZone(zone).toLocalDate()

        // Count existing camera posts on the same local day (before this one was inserted).
        val priorCount = postDao.countCameraPostsOnDay(userId, localDay, zone)

        // The current post is already inserted (same transaction), so subtract 1 to count prior.
        val priorRewarded = (priorCount - 1).coerceAtLeast(0)

        if (priorRewarded < DAILY_CAMERA_CAP) {
            userDao.incrementSpotScore(userId, CAMERA_POINTS)
        }
        // Always advance the streak regardless of cap (posting still counts for the day).
        userDao.advanceStreak(userId, localDay)
    }

    override suspend fun onPostLiked(postOwnerId: UUID, likerId: UUID) {
        if (postOwnerId == likerId) return
        userDao.incrementSpotScore(postOwnerId, LIKE_POINTS)
    }

    override suspend fun onPostUnliked(postOwnerId: UUID, unlikerId: UUID) {
        if (postOwnerId == unlikerId) return
        userDao.incrementSpotScore(postOwnerId, -LIKE_POINTS)
    }

    override suspend fun onFirstCommentByUser(postOwnerId: UUID, commenterId: UUID) {
        if (postOwnerId == commenterId) return
        userDao.incrementSpotScore(postOwnerId, COMMENT_POINTS)
    }

    private fun resolveZone(tz: String?): ZoneId {
        if (tz.isNullOrBlank()) return ZoneOffset.UTC
        return try {
            ZoneId.of(tz)
        } catch (e: Exception) {
            logger.warn("Invalid createdAtTimezone '{}', falling back to UTC", tz)
            ZoneOffset.UTC
        }
    }
}
