package com.carspotter.features.auth.dto

import com.carspotter.features.auth.AuthProvider
import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val email: String? = null,
    val password: String? = null,
    val provider: AuthProvider,
    val googleIdToken: String? = null
)