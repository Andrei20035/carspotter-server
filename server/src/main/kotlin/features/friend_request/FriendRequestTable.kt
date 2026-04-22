package com.carspotter.features.friend_request

import com.carspotter.features.user.UserTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp

object FriendRequestTable : Table("friend_requests") {
    val senderId = uuid("sender_id").references(UserTable.id, onDelete = ReferenceOption.CASCADE)
    val receiverId = uuid("receiver_id").references(UserTable.id, onDelete = ReferenceOption.CASCADE)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(senderId, receiverId)
}