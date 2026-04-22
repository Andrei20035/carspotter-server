package com.carspotter.features.auth

import org.jetbrains.exposed.dao.id.UUIDTable

object AuthTable: UUIDTable("auth_credentials") {
    val email = varchar("email", 255).uniqueIndex()
    val password = text("password").nullable()
    val provider = varchar("provider", 20).default(AuthProvider.REGULAR.name)
    val googleId = text("google_id").nullable()
}