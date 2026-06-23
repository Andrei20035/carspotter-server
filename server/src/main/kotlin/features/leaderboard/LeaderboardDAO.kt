package com.carspotter.features.leaderboard

import com.carspotter.features.user.UserTable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.util.UUID

interface ILeaderboardDAO {
    /** Return top [limit] users ordered by spotScore DESC, then id ASC. No avatar URL resolution. */
    suspend fun getTopEntries(limit: Int): List<RawLeaderboardEntry>

    /** Compute the rank of [userId]: count(users whose spotScore > mine) + 1. */
    suspend fun getUserRank(userId: UUID): Int

    /** Fetch score and streak for [userId]. */
    suspend fun getUserScoreAndStreak(userId: UUID): UserScoreStreak?
}

data class RawLeaderboardEntry(
    val userId: UUID,
    val username: String,
    val profilePicturePath: String?,
    val spotScore: Int,
    val currentStreak: Int,
    val lastStreakDate: LocalDate?,
    val lastStreakTimezone: String?,
)

data class UserScoreStreak(
    val userId: UUID,
    val username: String,
    val profilePicturePath: String?,
    val spotScore: Int,
    val currentStreak: Int,
    val lastStreakDate: LocalDate?,
    val lastStreakTimezone: String?,
)

class LeaderboardDAO : ILeaderboardDAO {

    override suspend fun getTopEntries(limit: Int): List<RawLeaderboardEntry> = transaction {
        UserTable
            .select(listOf(
                UserTable.id,
                UserTable.username,
                UserTable.profilePicturePath,
                UserTable.spotScore,
                UserTable.currentStreak,
                UserTable.lastStreakDate,
                UserTable.lastStreakTimezone,
            ))
            .orderBy(UserTable.spotScore to SortOrder.DESC, UserTable.id to SortOrder.ASC)
            .limit(limit)
            .map {
                RawLeaderboardEntry(
                    userId = it[UserTable.id].value,
                    username = it[UserTable.username],
                    profilePicturePath = it[UserTable.profilePicturePath],
                    spotScore = it[UserTable.spotScore],
                    currentStreak = it[UserTable.currentStreak],
                    lastStreakDate = it[UserTable.lastStreakDate],
                    lastStreakTimezone = it[UserTable.lastStreakTimezone],
                )
            }
    }

    override suspend fun getUserRank(userId: UUID): Int = transaction {
        val myScore = UserTable
            .select(UserTable.spotScore)
            .where { UserTable.id eq userId }
            .singleOrNull()
            ?.get(UserTable.spotScore) ?: 0

        val ahead = UserTable
            .select(UserTable.id)
            .where { UserTable.spotScore greater myScore }
            .count()

        (ahead + 1).toInt()
    }

    override suspend fun getUserScoreAndStreak(userId: UUID): UserScoreStreak? = transaction {
        UserTable
            .select(listOf(
                UserTable.id,
                UserTable.username,
                UserTable.profilePicturePath,
                UserTable.spotScore,
                UserTable.currentStreak,
                UserTable.lastStreakDate,
                UserTable.lastStreakTimezone,
            ))
            .where { UserTable.id eq userId }
            .singleOrNull()
            ?.let {
                UserScoreStreak(
                    userId = it[UserTable.id].value,
                    username = it[UserTable.username],
                    profilePicturePath = it[UserTable.profilePicturePath],
                    spotScore = it[UserTable.spotScore],
                    currentStreak = it[UserTable.currentStreak],
                    lastStreakDate = it[UserTable.lastStreakDate],
                    lastStreakTimezone = it[UserTable.lastStreakTimezone],
                )
            }
    }
}
