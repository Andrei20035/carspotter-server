package com.carspotter.features.auth.dto

import com.carspotter.features.auth.AuthProvider
import kotlinx.serialization.Serializable

@Serializable
class LoginRequest (
    val email: String? = null,
    val password: String? = null,
    val googleIdToken: String? = null,
    val provider: AuthProvider,
    val deviceId: String? = null,
    val deviceName: String? = null
)
