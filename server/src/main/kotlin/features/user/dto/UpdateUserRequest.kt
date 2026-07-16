package com.revio.server.features.user.dto

import com.revio.server.core.serialization.LocalDateSerializer
import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
data class UpdateUserRequest(
    val fullName: String? = null,
    val username: String? = null,
    val country: String? = null,
    val phoneNumber: String? = null,
    @Serializable(with = LocalDateSerializer::class)
    val birthDate: LocalDate? = null,
)
