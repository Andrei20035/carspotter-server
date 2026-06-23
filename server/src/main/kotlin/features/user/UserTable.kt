package com.carspotter.features.user

import com.carspotter.features.auth.AuthTable
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.timestamp

object UserTable : UUIDTable("users") {
    val authCredentialId = uuid("auth_credential_id").uniqueIndex().references(AuthTable.id, onDelete = ReferenceOption.CASCADE)
    val profilePicturePath = text("profile_picture_path").nullable()
    val fullName = varchar("full_name", 150)
    val phoneNumber = varchar("phone_number", 20).nullable()
    val birthDate = date("birth_date")
    val username = varchar("username", 50)
    val country = varchar("country", 50)
    val spotScore = integer("spot_score").default(0)
    val currentStreak = integer("current_streak").default(0)
    val longestStreak = integer("longest_streak").default(0)
    val lastStreakDate = date("last_streak_date").nullable()
    val lastStreakTimezone = varchar("last_streak_timezone", 64).nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)
}