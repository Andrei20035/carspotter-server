package com.carspotter.features.leaderboard

import com.carspotter.features.user.UserTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.util.UUID

interface ILeaderboardSnapshotDAO {
    /**
     * Snapshot every user's rank for [snapshotDate].
     * Idempotent: existing rows for that date are deleted first.
     * Returns the number of rows written.
     */
    suspend fun snapshotAllRanks(snapshotDate: LocalDate): Int

    /**
     * Return the rank from the most recent snapshot strictly before [beforeDate],
     * or null if no prior snapshot exists for [userId].
     */
    suspend fun getPreviousRank(userId: UUID, beforeDate: LocalDate): Int?
}

class LeaderboardSnapshotDAO : ILeaderboardSnapshotDAO {

    override suspend fun snapshotAllRanks(snapshotDate: LocalDate): Int = transaction {
        // Delete any existing snapshot for this date (idempotent re-run).
        LeaderboardSnapshotTable.deleteWhere { LeaderboardSnapshotTable.snapshotDate eq snapshotDate }

        // Fetch all users ordered by score DESC, id ASC (tie-breaking).
        data class UserScore(val id: UUID, val score: Int)

        val users = UserTable
            .select(listOf(UserTable.id, UserTable.spotScore))
            .orderBy(UserTable.spotScore to SortOrder.DESC, UserTable.id to SortOrder.ASC)
            .map { UserScore(it[UserTable.id].value, it[UserTable.spotScore]) }

        if (users.isEmpty()) return@transaction 0

        // Compute rank using strictly-greater definition (ties share a rank).
        data class RankedUser(val id: UUID, val score: Int, val rank: Int)

        val ranked = mutableListOf<RankedUser>()
        for (user in users) {
            val rank = ranked.count { it.score > user.score } + 1
            ranked.add(RankedUser(user.id, user.score, rank))
        }

        LeaderboardSnapshotTable.batchInsert(ranked) { entry ->
            this[LeaderboardSnapshotTable.snapshotDate] = snapshotDate
            this[LeaderboardSnapshotTable.userId] = entry.id
            this[LeaderboardSnapshotTable.rank] = entry.rank
            this[LeaderboardSnapshotTable.spotScore] = entry.score
        }

        ranked.size
    }

    override suspend fun getPreviousRank(userId: UUID, beforeDate: LocalDate): Int? = transaction {
        LeaderboardSnapshotTable
            .select(listOf(LeaderboardSnapshotTable.rank, LeaderboardSnapshotTable.snapshotDate))
            .where {
                (LeaderboardSnapshotTable.userId eq userId) and
                (LeaderboardSnapshotTable.snapshotDate less beforeDate)
            }
            .orderBy(LeaderboardSnapshotTable.snapshotDate to SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?.get(LeaderboardSnapshotTable.rank)
    }
}
