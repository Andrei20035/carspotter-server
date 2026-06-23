package com.carspotter.features.auth.dto

import kotlinx.serialization.Serializable

@Serializable
data class RefreshRequest(
    val refreshToken: String,
    val deviceId: String? = null,
)
