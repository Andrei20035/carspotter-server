package com.carspotter.features.comment

import com.carspotter.features.post.PostTable
import com.carspotter.features.user.UserTable
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp

object CommentTable : UUIDTable("comments") {
    val userId = uuid("user_id").references(UserTable.id, onDelete = ReferenceOption.CASCADE)
    val postId = uuid("post_id").references(PostTable.id, onDelete = ReferenceOption.CASCADE)
    val commentText = text("comment_text")
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)
}