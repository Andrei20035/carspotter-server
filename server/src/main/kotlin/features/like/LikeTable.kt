package com.carspotter.features.like

import com.carspotter.features.post.PostTable
import com.carspotter.features.user.UserTable
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp

object LikeTable : UUIDTable("likes") {
    val userId = uuid("user_id").references(UserTable.id, onDelete = ReferenceOption.CASCADE)
    val postId = uuid("post_id").references(PostTable.id, onDelete = ReferenceOption.CASCADE)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)

    init {
        uniqueIndex(userId, postId)
    }
}