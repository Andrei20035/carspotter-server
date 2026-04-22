package com.carspotter.features.auth.dto

import kotlinx.serialization.Serializable

@Serializable
data class UpdatePasswordRequest(
    val newPassword: String
)