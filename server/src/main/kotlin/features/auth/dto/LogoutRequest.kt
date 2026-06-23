package com.carspotter.features.auth.dto

import kotlinx.serialization.Serializable

@Serializable
data class LogoutRequest(
    val sessionId: String? = null,
)
