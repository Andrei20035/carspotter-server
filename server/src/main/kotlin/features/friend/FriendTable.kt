package com.carspotter.features.friend

import com.carspotter.features.user.UserTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp

object FriendTable : Table("friends") {
    val userId = uuid("user_id").references(UserTable.id, onDelete = ReferenceOption.CASCADE)
    val friendId = uuid("friend_id").references(UserTable.id, onDelete = ReferenceOption.CASCADE)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)


    override val primaryKey = PrimaryKey(userId, friendId)

    init {
        check("chk_no_self_friendship") { userId neq friendId }
    }

}