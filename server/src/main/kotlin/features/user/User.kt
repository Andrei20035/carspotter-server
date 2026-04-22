package com.carspotter.features.user

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class User(
    val id: UUID = UUID.randomUUID(),
    val authCredentialId: UUID,
    val profilePicturePath: String? = null,
    val fullName: String,
    val phoneNumber: String?,
    val birthDate: LocalDate,
    val username: String,
    val country: String,
    val spotScore: Int = 0,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
)