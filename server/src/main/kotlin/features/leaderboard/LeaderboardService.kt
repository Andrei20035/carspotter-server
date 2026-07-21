package com.revio.server.features.leaderboard

import com.revio.server.core.storage.IStorageService
import com.revio.server.core.util.resolveZone
import com.revio.server.features.leaderboard.dto.CurrentUserStandingDTO
import com.revio.server.features.leaderboard.dto.LeaderboardEntryDTO
import com.revio.server.features.leaderboard.dto.LeaderboardResponseDTO
import java.time.Instant
import java.util.UUID

interface ILeaderboardService {
    suspend fun getLeaderboard(currentUserId: UUID, limit: Int): LeaderboardResponseDTO

    /** Rank of a single user, without fetching the rest of the board. */
    suspend fun getUserRank(userId: UUID): Int
}

class LeaderboardService(
    private val leaderboardDao: ILeaderboardDAO,
    private val snapshotDao: ILeaderboardSnapshotDAO,
    private val storageService: IStorageService,
    private val snapshotZoneId: String? = System.getenv("LEADERBOARD_SNAPSHOT_ZONE"),
) : ILeaderboardService {

    override suspend fun getLeaderboard(currentUserId: UUID, limit: Int): LeaderboardResponseDTO {
        val now = Instant.now()

        val rawEntries = leaderboardDao.getTopEntries(limit)
        val entries = rawEntries.mapIndexed { index, raw ->
            val rank = index + 1
            LeaderboardEntryDTO(
                userId = raw.userId,
                rank = rank,
                username = raw.username,
                avatarUrl = raw.profilePicturePath?.let(storageService::resolveUrl),
                spotScore = raw.spotScore,
                streakDays = StreakCalculator.displayedStreak(
                    raw.currentStreak, raw.lastStreakDate, raw.lastStreakTimezone, now
                ),
            )
        }

        val userStats = leaderboardDao.getUserScoreAndStreak(currentUserId)
        val currRank = leaderboardDao.getUserRank(currentUserId)
        val today = now.atZone(resolveZone(snapshotZoneId)).toLocalDate()
        val prevRank = snapshotDao.getPreviousRank(currentUserId, today)
        val (movement, placesMoved) = RankMovement.of(currRank, prevRank)

        val currentUserEntry = LeaderboardEntryDTO(
            userId = currentUserId,
            rank = currRank,
            username = userStats?.username ?: "",
            avatarUrl = userStats?.profilePicturePath?.let(storageService::resolveUrl),
            spotScore = userStats?.spotScore ?: 0,
            streakDays = StreakCalculator.displayedStreak(
                userStats?.currentStreak ?: 0,
                userStats?.lastStreakDate,
                userStats?.lastStreakTimezone,
                now,
            ),
        )
        val currentUser = CurrentUserStandingDTO(
            entry = currentUserEntry,
            movement = movement,
            placesMoved = placesMoved,
        )

        return LeaderboardResponseDTO(currentUser = currentUser, entries = entries)
    }

    override suspend fun getUserRank(userId: UUID): Int = leaderboardDao.getUserRank(userId)
}
