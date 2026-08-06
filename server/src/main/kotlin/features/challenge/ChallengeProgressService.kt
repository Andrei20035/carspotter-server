package com.revio.server.features.challenge

import java.time.Instant
import java.util.UUID

interface IChallengeProgressService {
    /**
     * Evaluates [postId] against whatever challenge was active at [postCreatedAt] (found via
     * [IChallengeDAO.findActive] using that same instant, so "is there an active challenge" and
     * "is this post inside its window" can never disagree due to clock skew between the two
     * checks). Returns null when no challenge was active — nothing to evaluate.
     *
     * Callers (PostService, Etapa 4) are expected to have already confirmed the post is
     * PostSource.CAMERA and has a non-null carModelId before calling this — those are cheap,
     * challenge-independent preconditions the plan's §4.3 assigns to the caller.
     */
    suspend fun evaluatePostForActiveChallenge(
        userId: UUID,
        postId: UUID,
        carModelId: UUID,
        postCreatedAt: Instant,
    ): ContributionEvaluation?

    /**
     * The [ChallengeProgressService.REVOKE_AFTER_END] policy, as a predicate over a challenge's
     * endsAt, for the post-removal transaction to apply while reconciling contributions.
     *
     * Handed to the DAO rather than executed here because reconciliation must run inside the same
     * transaction that deletes the post (see [IPostRemovalDAO.removePostAtomically]) — but the
     * policy decision itself stays in this service, so it applies uniformly no matter which
     * caller removes the post (its author, or a moderator takedown).
     */
    fun contributionRevokePolicy(): (endsAt: Instant) -> Boolean

    /** [userId]'s progress on [challengeId] — zero/NONE if they have no participant row yet. */
    suspend fun getUserProgress(challengeId: UUID, userId: UUID): ParticipantProgress

    /** [userId]'s contributing posts for [challengeId], oldest first. */
    suspend fun listUserContributions(challengeId: UUID, userId: UUID): List<ContributionSummary>
}

class ChallengeProgressService(
    private val challengeDao: IChallengeDAO,
    private val challengeProgressDao: IChallengeProgressDAO,
) : IChallengeProgressService {

    companion object {
        /**
         * Whether deleting/invalidating a contributing post after its challenge's endsAt still
         * revokes the completion reward. true = revoke (the decided MVP behavior — see the
         * plan's §2.6a: a challenge completed correctly during its window, but abandoned by
         * deleting a contributing post afterward, loses the reward; the post can no longer be
         * replaced once endsAt has passed). false would instead freeze already-granted rewards
         * once a challenge ends. Kept as a single flag, not spread across deletion call sites, so
         * this policy can change later without a migration or touching any deletion path.
         */
        const val REVOKE_AFTER_END = true
    }

    override suspend fun evaluatePostForActiveChallenge(
        userId: UUID,
        postId: UUID,
        carModelId: UUID,
        postCreatedAt: Instant,
    ): ContributionEvaluation? {
        val activeChallenge = challengeDao.findActive(postCreatedAt) ?: return null
        return challengeProgressDao.evaluatePostContribution(
            challengeId = activeChallenge.id,
            userId = userId,
            postId = postId,
            carModelId = carModelId,
            postCreatedAt = postCreatedAt,
        )
    }

    override fun contributionRevokePolicy(): (endsAt: Instant) -> Boolean {
        val now = Instant.now()
        return { endsAt -> REVOKE_AFTER_END || now.isBefore(endsAt) }
    }

    override suspend fun getUserProgress(challengeId: UUID, userId: UUID): ParticipantProgress =
        challengeProgressDao.getProgress(challengeId, userId)
            ?: ParticipantProgress(contributionCount = 0, rewardState = RewardState.NONE)

    override suspend fun listUserContributions(challengeId: UUID, userId: UUID): List<ContributionSummary> =
        challengeProgressDao.listContributionsForUser(challengeId, userId)
}
