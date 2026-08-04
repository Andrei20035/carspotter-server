package com.revio.server.features.feedback

import com.revio.server.features.user.UserTable
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp

object UserFeedbackTable : UUIDTable("user_feedback") {
    val userId = uuid("user_id").references(UserTable.id, onDelete = ReferenceOption.CASCADE)
    val category = varchar("category", 32)
    val area = varchar("area", 32).nullable()
    val message = varchar("message", 4000)
    val secondaryMessage = varchar("secondary_message", 4000).nullable()
    val quickReason = varchar("quick_reason", 40).nullable()
    val priority = varchar("priority", 20).nullable()
    val rating = short("rating").nullable()
    val keepMessage = varchar("keep_message", 2000).nullable()
    val improveMessage = varchar("improve_message", 2000).nullable()
    val feedbackSource = varchar("source", 32)
    val originScreen = varchar("origin_screen", 60).nullable()
    val includeDiagnostics = bool("include_diagnostics").default(false)
    val appVersion = varchar("app_version", 30).nullable()
    val androidVersion = varchar("android_version", 20).nullable()
    val deviceModel = varchar("device_model", 60).nullable()
    val connectionType = varchar("connection_type", 20).nullable()
    val lastErrorCode = varchar("last_error_code", 60).nullable()
    val screenshotKey = text("screenshot_key").nullable()
    val clientFeedbackId = uuid("client_feedback_id")
    val status = varchar("status", 24).default("NEW")
    val clientSubmittedAt = timestamp("client_submitted_at").nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)

    init {
        uniqueIndex(clientFeedbackId)
    }
}
