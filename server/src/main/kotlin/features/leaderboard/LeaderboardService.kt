package com.carspotter.features.leaderboard

import com.carspotter.core.storage.IStorageService
import com.carspotter.features.leaderboard.dto.CurrentUserStandingDTO
import com.carspotter.features.leaderboard.dto.LeaderboardEntryDTO
import com.carspotter.features.leaderboard.dto.LeaderboardResponseDTO
import java.util.UUID

interface ILeaderboardService {
    suspend fun getLeaderboard(currentUserId: UUID, limit: Int): LeaderboardResponseDTO
}

class LeaderboardService(
    private val leaderboardDao: ILeaderboardDAO,
    private val storageService: IStorageService,
) : ILeaderboardService {

    override suspend fun getLeaderboard(currentUserId: UUID, limit: Int): LeaderboardResponseDTO {
        val rawEntries = leaderboardDao.getTopEntries(limit)
        val entries = rawEntries.mapIndexed { index, raw ->
            LeaderboardEntryDTO(
                userId = raw.userId,
                rank = index + 1,
                username = raw.username,
                avatarUrl = raw.profilePicturePath?.let(storageService::resolveUrl),
                spotScore = raw.spotScore,
                streakDays = raw.currentStreak,
            )
        }

        val userStats = leaderboardDao.getUserScoreAndStreak(currentUserId)
        val userRank = leaderboardDao.getUserRank(currentUserId)
        val currentUserEntry = LeaderboardEntryDTO(
            userId = currentUserId,
            rank = userRank,
            username = userStats?.username ?: "",
            avatarUrl = userStats?.profilePicturePath?.let(storageService::resolveUrl),
            spotScore = userStats?.spotScore ?: 0,
            streakDays = userStats?.currentStreak ?: 0,
        )
        val currentUser = CurrentUserStandingDTO(
            entry = currentUserEntry,
            movement = "KEEP",
            placesMoved = 0,
            // TODO(leaderboard): implement real movement via leaderboard_rank_snapshots + daily job.
        )

        return LeaderboardResponseDTO(currentUser = currentUser, entries = entries)
    }
}
