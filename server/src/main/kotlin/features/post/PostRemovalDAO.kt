package com.revio.server.features.post

import com.revio.server.core.db.retrySerializationConflicts
import com.revio.server.features.challenge.ContributionReversal
import com.revio.server.features.challenge.IChallengeProgressDAO
import com.revio.server.features.scoring.IScoringDao
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

/**
 * What [IPostRemovalDAO.removePostAtomically] committed. [deletedRows] is 0 when the post was
 * already gone, in which case nothing else happened either.
 */
data class PostRemovalOutcome(
    val deletedRows: Int,
    val reversals: List<ContributionReversal>,
)

interface IPostRemovalDAO {
    /**
     * Removes a post and everything that must fall with it, in ONE transaction: read the post's
     * owner and points, reconcile every challenge contribution it made (revoking the completion
     * reward where [allowRevoke] permits), reverse its normal points from spot_score, then delete
     * the row.
     *
     * Atomicity is the whole point, not an optimization. challenge_contributions.post_id is ON
     * DELETE CASCADE: if the delete could commit while reconciliation had failed, the cascade
     * would erase the contribution rows with no chance to revoke the reward they earned, leaving
     * challenge_participants.contribution_count and reward_state permanently disagreeing with
     * reality and spot_score inflated by a reward the user no longer qualifies for. Nothing
     * recomputes that afterwards, and the ledger invariant still holds, so no reconciliation job
     * would even detect it. Either both happen or neither does; on failure the caller sees the
     * exception and the post is still there to retry.
     *
     * Reading points inside this transaction (rather than from a row the caller fetched earlier)
     * also closes the pre-existing race where a concurrent like made the caller's post.points
     * stale between its read and the reversal.
     *
     * Ordering within the transaction is deliberate: the challenge reward is revoked BEFORE the
     * post's normal points are subtracted, so the floor-at-zero clamp on spot_score produces a
     * deterministic result rather than one that depends on which reversal ran first.
     */
    suspend fun removePostAtomically(postId: UUID, allowRevoke: (endsAt: Instant) -> Boolean): PostRemovalOutcome
}

class PostRemovalDAO(
    private val challengeProgressDao: IChallengeProgressDAO,
    private val scoringDao: IScoringDao,
) : IPostRemovalDAO {

    // The retry wraps the entire unit, so a serialization conflict (REPEATABLE READ) re-runs
    // reconciliation and deletion together from a fresh snapshot. Nothing was committed by the
    // aborted attempt, so replaying it is safe.
    override suspend fun removePostAtomically(
        postId: UUID,
        allowRevoke: (endsAt: Instant) -> Boolean,
    ): PostRemovalOutcome = retrySerializationConflicts {
        transaction {
            val post = PostTable
                .select(PostTable.userId, PostTable.points)
                .where { PostTable.id eq postId }
                .singleOrNull()
                ?: return@transaction PostRemovalOutcome(deletedRows = 0, reversals = emptyList())

            val reversals = challengeProgressDao.removeContributionsForPostInCurrentTransaction(postId, allowRevoke)

            val deletedRows = scoringDao.reverseAndDeletePostInCurrentTransaction(
                ownerId = post[PostTable.userId],
                postId = postId,
                points = post[PostTable.points],
            )

            PostRemovalOutcome(deletedRows = deletedRows, reversals = reversals)
        }
    }
}
