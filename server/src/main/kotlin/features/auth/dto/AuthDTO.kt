package com.carspotter.features.auth.dto

import com.carspotter.features.auth.AuthCredential
import com.carspotter.features.auth.AuthProvider
import java.util.*

data class AuthDTO(
    val id: UUID,
    val email: String,
    val provider: AuthProvider,
    val userId: UUID? = null,
)

fun AuthCredential.toDTO(userId: UUID? = null): AuthDTO {
    return AuthDTO(
        id = this.id!!,
        email = this.email,
        provider = this.provider,
        userId = userId
    )
}
