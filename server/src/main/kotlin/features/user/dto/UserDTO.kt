package com.carspotter.features.user.dto

import com.carspotter.features.user.User
import com.carspotter.core.serialization.InstantSerializer
import com.carspotter.core.serialization.LocalDateSerializer
import com.carspotter.core.serialization.UUIDSerializer
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.util.*

@Serializable
data class UserDTO(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID = UUID.randomUUID(),
    val fullName: String,
    val phoneNumber: String? = null,
    val profilePicturePath: String? = null,
    @Serializable(with = LocalDateSerializer::class)
    val birthDate: LocalDate,
    val username: String,
    val country: String,
    val spotScore: Int = 0,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant? = null,
    @Serializable(with = InstantSerializer::class)
    val updatedAt: Instant? = null
)

fun User.toDTO(): UserDTO {
    return UserDTO(
        id = this.id,
        fullName = this.fullName,
        phoneNumber = this.phoneNumber,
        profilePicturePath = this.profilePicturePath,
        birthDate = this.birthDate,
        username = this.username,
        country = this.country,
        spotScore = this.spotScore,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt
    )
}