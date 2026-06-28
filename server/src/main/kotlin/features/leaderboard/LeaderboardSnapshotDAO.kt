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

        // Fetch all users in canonical leaderboard order; rank = position in this list.
        data class RankedUser(val id: UUID, val score: Int, val rank: Int)

        val ranked = UserTable
            .select(listOf(UserTable.id, UserTable.spotScore))
            .orderBy(*LEADERBOARD_ORDER)
            .mapIndexed { index, row ->
                RankedUser(row[UserTable.id].value, row[UserTable.spotScore], index + 1)
            }

        if (ranked.isEmpty()) return@transaction 0

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
