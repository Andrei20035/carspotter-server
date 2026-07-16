package com.revio.server.features.report

import com.revio.server.features.post.PostTable
import com.revio.server.features.user.UserTable
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp

object ReportTable : UUIDTable("reports") {
    val reporterId = uuid("reporter_id").references(UserTable.id, onDelete = ReferenceOption.CASCADE)
    val postId = uuid("post_id").references(PostTable.id, onDelete = ReferenceOption.CASCADE)
    val reason = enumerationByName("reason", 30, ReportReason::class)
    val status = enumerationByName("status", 20, ReportStatus::class)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)

    init {
        // One report per user/post/reason — makes a repeated report an idempotent no-op.
        uniqueIndex(reporterId, postId, reason)
    }
}
