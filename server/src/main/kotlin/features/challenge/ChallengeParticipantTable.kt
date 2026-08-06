package com.revio.server.features.challenge

import com.revio.server.features.user.UserTable
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

/**
 * One row per (challenge, user). This is the serialization point for reward evaluation: it is
 * locked with SELECT ... FOR UPDATE while a post is evaluated (ChallengeProgressDAO), so two
 * concurrent posts from the same user in the same challenge can't both observe a stale count and
 * both trigger a grant. award_epoch is bumped on every revoke so a later re-grant gets a fresh
 * idempotency key in challenge_reward_ledger.
 */
object ChallengeParticipantTable : UUIDTable("challenge_participants") {
    val challengeId = uuid("challenge_id").references(ChallengeTable.id, onDelete = ReferenceOption.CASCADE)
    val userId = uuid("user_id").references(UserTable.id, onDelete = ReferenceOption.CASCADE)
    val contributionCount = integer("contribution_count").default(0)
    val rewardState = enumerationByName("reward_state", 16, RewardState::class).default(RewardState.NONE)
    val awardEpoch = integer("award_epoch").default(0)
    val firstCompletedAt = timestampWithTimeZone("first_completed_at").nullable()
    val lastGrantedAt = timestampWithTimeZone("last_granted_at").nullable()
    val updatedAt = timestampWithTimeZone("updated_at").nullable()

    init {
        uniqueIndex(challengeId, userId)
    }
}
