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
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val lastStreakDate: LocalDate? = null,
    val lastStreakTimezone: String? = null,
    val isEarlySpotter: Boolean = false,
    val earlySpotterNumber: Int? = null,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
)