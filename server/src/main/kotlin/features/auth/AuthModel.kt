package com.carspotter.features.auth

import java.util.*

enum class AuthProvider {
    GOOGLE, REGULAR
}

data class AuthCredential(
    val id: UUID = UUID.randomUUID(),
    val email: String,
    val password: String?,
    val provider: AuthProvider,
    val googleId: String?,
)