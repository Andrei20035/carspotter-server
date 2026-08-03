package com.revio.server.features.feedback

import com.revio.server.features.user.UserTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp

object FeedbackPromptStateTable : Table("feedback_prompt_state") {
    val userId = uuid("user_id").references(UserTable.id, onDelete = ReferenceOption.CASCADE)
    val promptKey = varchar("prompt_key", 40)
    val status = varchar("status", 20)
    val shownCount = short("shown_count").default(0)
    val lastShownAt = timestamp("last_shown_at").nullable()
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(userId, promptKey)
}
