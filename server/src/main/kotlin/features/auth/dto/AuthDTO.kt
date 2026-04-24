package com.carspotter.features.auth.dto

import com.carspotter.features.auth.AuthCredential
import com.carspotter.features.auth.AuthProvider
import java.util.*

data class AuthDTO(
    val id: UUID,
    val email: String,
    val provider: AuthProvider,
)

fun AuthCredential.toDTO(): AuthDTO {
    return AuthDTO(
        id = this.id!!,
        email = this.email,
        provider = this.provider,
    )
}