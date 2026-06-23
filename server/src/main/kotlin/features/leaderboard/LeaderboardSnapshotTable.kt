package com.carspotter.features.leaderboard

import com.carspotter.features.user.UserTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.date

object LeaderboardSnapshotTable : Table("leaderboard_rank_snapshots") {
    val snapshotDate = date("snapshot_date")
    val userId = uuid("user_id").references(UserTable.id, onDelete = ReferenceOption.CASCADE)
    val rank = integer("rank")
    val spotScore = integer("spot_score")

    override val primaryKey = PrimaryKey(snapshotDate, userId)
}
