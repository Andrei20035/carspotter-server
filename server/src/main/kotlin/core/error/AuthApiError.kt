package com.carspotter.core.error

import kotlinx.serialization.Serializable

@Serializable
data class AuthApiError(
    val code: AuthErrorCode,
    val message: String
)

@Serializable
data class AuthErrorResponse(
    val error: AuthApiError
)
