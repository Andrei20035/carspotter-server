package com.carspotter.features.friend

import com.carspotter.features.user.UserTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp

object FriendTable : Table("friends") {
    val userId1 = uuid("user_id_1").references(UserTable.id, onDelete = ReferenceOption.CASCADE)
    val userId2 = uuid("user_id_2").references(UserTable.id, onDelete = ReferenceOption.CASCADE)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(userId1, userId2)
}