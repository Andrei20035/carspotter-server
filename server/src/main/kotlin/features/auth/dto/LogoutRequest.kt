package com.revio.server.features.auth.dto

import kotlinx.serialization.Serializable

@Serializable
data class LogoutRequest(
    val sessionId: String? = null,
)
