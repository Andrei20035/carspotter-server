package com.carspotter.features.leaderboard

import com.carspotter.features.user.UserTable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.util.UUID

/** Canonical leaderboard sort order: higher score first, stable tie-break by user id. */
val LEADERBOARD_ORDER = arrayOf(
    UserTable.spotScore to SortOrder.DESC,
    UserTable.id to SortOrder.ASC,
)

interface ILeaderboardDAO {
    /** Return top [limit] users ordered by [LEADERBOARD_ORDER]. No avatar URL resolution. */
    suspend fun getTopEntries(limit: Int): List<RawLeaderboardEntry>

    /** Return the unique 1-based position of [userId] in the full leaderboard ordered by [LEADERBOARD_ORDER]. */
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
            .orderBy(*LEADERBOARD_ORDER)
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
        data class MySelf(val score: Int, val id: UUID)

        val me = UserTable
            .select(listOf(UserTable.id, UserTable.spotScore))
            .where { UserTable.id eq userId }
            .singleOrNull()
            ?.let { MySelf(it[UserTable.spotScore], it[UserTable.id].value) }
            ?: return@transaction Int.MAX_VALUE

        // Users strictly ahead in LEADERBOARD_ORDER (spotScore DESC, id ASC):
        // either their score is higher, or scores are equal and their id sorts before mine.
        val ahead = UserTable
            .select(UserTable.id)
            .where {
                (UserTable.spotScore greater me.score) or
                ((UserTable.spotScore eq me.score) and (UserTable.id less me.id))
            }
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
