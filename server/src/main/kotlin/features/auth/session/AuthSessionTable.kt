package com.carspotter.features.auth.session

import com.carspotter.features.auth.AuthTable
import com.carspotter.features.user.UserTable
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

object AuthSessionTable : UUIDTable("auth_sessions") {
    val credentialId = reference("credential_id", AuthTable, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE).nullable()
    val version = integer("version").default(1)
    val scope = varchar("scope", 20).default(SessionScope.FULL.name)
    val refreshTokenHash = char("refresh_token_hash", 64)
    val prevTokenHash = char("prev_token_hash", 64).nullable()
    val prevRotatedAt = timestampWithTimeZone("prev_rotated_at").nullable()
    val status = varchar("status", 20).default(SessionStatus.ACTIVE.name)
    val revokedReason = varchar("revoked_reason", 40).nullable()
    val deviceId = varchar("device_id", 128).nullable()
    val deviceName = varchar("device_name", 128).nullable()
    val userAgent = text("user_agent").nullable()
    val ipAddress = varchar("ip_address", 64).nullable()
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)
    val lastUsedAt = timestampWithTimeZone("last_used_at").defaultExpression(CurrentTimestampWithTimeZone)
    val idleExpiresAt = timestampWithTimeZone("idle_expires_at")
    val absoluteExpiresAt = timestampWithTimeZone("absolute_expires_at")
}
