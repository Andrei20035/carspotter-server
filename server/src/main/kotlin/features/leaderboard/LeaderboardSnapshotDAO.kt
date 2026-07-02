package com.carspotter.features.leaderboard

import com.carspotter.features.activity.ActivityEventType
import com.carspotter.features.user.UserTable
import features.activity.ActivityDAO
import features.activity.IActivityDAO
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
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

    /**
     * Return the spotScore from the most recent snapshot on or before [date],
     * or null if no such snapshot exists for [userId].
     */
    suspend fun getSpotScoreOnOrBefore(userId: UUID, date: LocalDate): Int?
}

class LeaderboardSnapshotDAO(
    private val activityDao: IActivityDAO = ActivityDAO(),
) : ILeaderboardSnapshotDAO {

    override suspend fun snapshotAllRanks(snapshotDate: LocalDate): Int {
        // Fetch all users in canonical leaderboard order; rank = position in this list.
        data class RankedUser(val id: UUID, val score: Int, val rank: Int)

        // `transaction { }` is a blocking (non-suspend) DSL, so the LEADERBOARD_UP writes below
        // (which go through the suspend IActivityDAO) can't happen inside it. Instead, the
        // transaction only computes which users qualify, and the suspend writes happen after
        // it closes.
        val (rowsWritten, leaderboardUpCandidates) = transaction {
            // Delete any existing snapshot for this date (idempotent re-run).
            LeaderboardSnapshotTable.deleteWhere { LeaderboardSnapshotTable.snapshotDate eq snapshotDate }

            val ranked = UserTable
                .select(listOf(UserTable.id, UserTable.spotScore))
                .orderBy(*LEADERBOARD_ORDER)
                .mapIndexed { index, row ->
                    RankedUser(row[UserTable.id].value, row[UserTable.spotScore], index + 1)
                }

            if (ranked.isEmpty()) return@transaction 0 to emptyList<Pair<UUID, Int>>()

            LeaderboardSnapshotTable.batchInsert(ranked) { entry ->
                this[LeaderboardSnapshotTable.snapshotDate] = snapshotDate
                this[LeaderboardSnapshotTable.userId] = entry.id
                this[LeaderboardSnapshotTable.rank] = entry.rank
                this[LeaderboardSnapshotTable.spotScore] = entry.score
            }

            // Users who moved up more than one place versus the most recent snapshot strictly
            // before this one — the only ones that should get a LEADERBOARD_UP milestone.
            val candidates = ranked.mapNotNull { entry ->
                val previousRank = getPreviousRankRow(entry.id, snapshotDate)
                val movement = RankMovement.of(entry.rank, previousRank)
                if (movement.movement == "UP" && movement.placesMoved > 1) {
                    entry.id to movement.placesMoved
                } else {
                    null
                }
            }

            ranked.size to candidates
        }

        // Idempotent per (user, LEADERBOARD_UP, snapshotDate) via recordEventIdempotent's
        // unique index, so re-running snapshotAllRanks for the same date never duplicates events.
        for ((userId, placesMoved) in leaderboardUpCandidates) {
            activityDao.recordEventIdempotent(userId, ActivityEventType.LEADERBOARD_UP, snapshotDate, placesMoved)
        }

        return rowsWritten
    }

    /** Same lookup as [getPreviousRank], usable inside an already-open transaction. */
    private fun getPreviousRankRow(userId: UUID, beforeDate: LocalDate): Int? =
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

    override suspend fun getPreviousRank(userId: UUID, beforeDate: LocalDate): Int? = transaction {
        getPreviousRankRow(userId, beforeDate)
    }

    override suspend fun getSpotScoreOnOrBefore(userId: UUID, date: LocalDate): Int? = transaction {
        LeaderboardSnapshotTable
            .select(listOf(LeaderboardSnapshotTable.spotScore, LeaderboardSnapshotTable.snapshotDate))
            .where {
                (LeaderboardSnapshotTable.userId eq userId) and
                (LeaderboardSnapshotTable.snapshotDate lessEq date)
            }
            .orderBy(LeaderboardSnapshotTable.snapshotDate to SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?.get(LeaderboardSnapshotTable.spotScore)
    }
}
