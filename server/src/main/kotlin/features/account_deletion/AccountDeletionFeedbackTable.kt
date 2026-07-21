package com.revio.server.features.account_deletion

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp

object AccountDeletionFeedbackTable : UUIDTable("account_deletion_feedback") {
    val reason = varchar("reason", 40)
    val details = text("details").nullable()
    val accountAgeDays = integer("account_age_days").nullable()
    val postCount = integer("post_count").nullable()
    val spotScore = integer("spot_score").nullable()
    val streakDays = integer("streak_days").nullable()
    val provider = varchar("provider", 20).nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
}
