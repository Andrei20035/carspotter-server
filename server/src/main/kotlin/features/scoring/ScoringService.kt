package com.carspotter.features.scoring

import com.carspotter.core.util.resolveZone
import com.carspotter.features.post.IPostDAO
import com.carspotter.features.post.PostSource
import com.carspotter.features.user.IUserDAO
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

interface IScoringService {
    /**
     * Award SpotScore and update streak for a newly created camera post.
     * No-ops silently when [source] is GALLERY.
     */
    suspend fun onPostCreated(
        userId: UUID,
        postId: UUID,
        source: PostSource,
        createdAtUtc: Instant,
        createdAtTimezone: String?,
    )

    /**
     * Award +[LIKE_POINTS] to [postOwnerId] when [likerId] likes a post.
     * No-op on self-likes or GALLERY posts.
     */
    suspend fun onPostLiked(postOwnerId: UUID, postId: UUID, likerId: UUID, source: PostSource)

    /**
     * Remove +[LIKE_POINTS] from [postOwnerId] when [unlikerId] removes a like.
     * No-op on self-unlike or GALLERY posts.
     */
    suspend fun onPostUnliked(postOwnerId: UUID, postId: UUID, unlikerId: UUID, source: PostSource)

    /**
     * Award +[COMMENT_POINTS] to [postOwnerId] for the first comment by [commenterId] on a post.
     * Self-comments, repeat commenters, and GALLERY posts award nothing.
     */
    suspend fun onFirstCommentByUser(postOwnerId: UUID, postId: UUID, commenterId: UUID, source: PostSource)
}

class ScoringServiceImpl(
    private val userDao: IUserDAO,
    private val postDao: IPostDAO,
    private val scoringDao: IScoringDao,
) : IScoringService {

    companion object {
        const val CAMERA_POINTS = 10
        const val DAILY_CAMERA_CAP = 10
        const val LIKE_POINTS = 1
        const val COMMENT_POINTS = 5
    }

    override suspend fun onPostCreated(
        userId: UUID,
        postId: UUID,
        source: PostSource,
        createdAtUtc: Instant,
        createdAtTimezone: String?,
    ) {
        if (source != PostSource.CAMERA) return

        val zone = resolveZone(createdAtTimezone)
        val localDay: LocalDate = createdAtUtc.atZone(zone).toLocalDate()

        // Count existing camera posts on the same local day (before this one was inserted).
        val priorCount = postDao.countCameraPostsOnDay(userId, localDay, zone)

        // The current post is already inserted, so subtract 1 to count prior rewarded posts.
        val priorRewarded = (priorCount - 1).coerceAtLeast(0)

        val points = if (priorRewarded < DAILY_CAMERA_CAP) CAMERA_POINTS else 0
        if (points > 0) {
            scoringDao.applyCreationPoints(userId, postId, points)
        }
        // Always advance the streak regardless of cap (posting still counts for the day).
        userDao.advanceStreak(userId, localDay, createdAtTimezone)
    }

    override suspend fun onPostLiked(postOwnerId: UUID, postId: UUID, likerId: UUID, source: PostSource) {
        if (postOwnerId == likerId || source != PostSource.CAMERA) return
        scoringDao.applyEngagementPoints(postOwnerId, postId, LIKE_POINTS)
    }

    override suspend fun onPostUnliked(postOwnerId: UUID, postId: UUID, unlikerId: UUID, source: PostSource) {
        if (postOwnerId == unlikerId || source != PostSource.CAMERA) return
        scoringDao.applyEngagementPoints(postOwnerId, postId, -LIKE_POINTS)
    }

    override suspend fun onFirstCommentByUser(postOwnerId: UUID, postId: UUID, commenterId: UUID, source: PostSource) {
        if (postOwnerId == commenterId || source != PostSource.CAMERA) return
        scoringDao.applyEngagementPoints(postOwnerId, postId, COMMENT_POINTS)
    }

}
