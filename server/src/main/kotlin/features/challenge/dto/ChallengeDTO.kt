package com.revio.server.features.challenge.dto

import com.revio.server.core.serialization.InstantSerializer
import com.revio.server.core.serialization.UUIDSerializer
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

/** Public-facing challenge config — no admin/audit fields (status, createdBy, timestamps). */
@Serializable
data class ChallengeDTO(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val title: String,
    val description: String?,
    val targetFamilyBrand: String,
    val targetFamilyName: String,
    val requiredPosts: Int,
    val rewardPoints: Int,
    @Serializable(with = InstantSerializer::class) val startsAt: Instant,
    @Serializable(with = InstantSerializer::class) val endsAt: Instant,
)

/** The viewer's own progress on one challenge. */
@Serializable
data class ChallengeProgressDTO(
    val contributionCount: Int,
    val rewardState: String,
)

@Serializable
data class ChallengeContributionDTO(
    @Serializable(with = UUIDSerializer::class) val postId: UUID,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant,
)

/** Response for GET /challenges/current. Both fields null when no challenge is scheduled at all. */
@Serializable
data class CurrentChallengeDTO(
    val challenge: ChallengeDTO?,
    val progress: ChallengeProgressDTO?,
)

/** Response for GET /challenges/{id}/progress. */
@Serializable
data class ChallengeProgressDetailDTO(
    val progress: ChallengeProgressDTO,
    val contributions: List<ChallengeContributionDTO>,
)
