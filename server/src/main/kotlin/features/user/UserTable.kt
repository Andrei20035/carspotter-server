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
    val username = varchar("username", 50).uniqueIndex()
    val country = varchar("country", 50)
    val spotScore = integer("spot_score").default(0)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)
}