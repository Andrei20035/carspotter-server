package com.revio.server.features.feedback

import com.revio.server.features.user.UserTable
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp

object FirstPostFeedbackTable : UUIDTable("first_post_feedback") {
    val userId = uuid("user_id").references(UserTable.id, onDelete = ReferenceOption.CASCADE)
    val feedbackType = varchar("feedback_type", 40).default("first_post_experience")
    val rating = short("rating")
    val quickReason = varchar("quick_reason", 40).nullable()
    val comment = varchar("comment", 1000).nullable()
    val surface = varchar("surface", 20).nullable()
    val appVersion = varchar("app_version", 30).nullable()
    val androidVersion = varchar("android_version", 20).nullable()
    val deviceModel = varchar("device_model", 60).nullable()
    val connectionType = varchar("connection_type", 20).nullable()
    val uploadDurationMs = integer("upload_duration_ms").nullable()
    val hadRetries = bool("had_retries").nullable()
    val lastErrorCode = varchar("last_error_code", 60).nullable()
    val clientSubmittedAt = timestamp("client_submitted_at").nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)

    init {
        uniqueIndex(userId, feedbackType)
    }
}
