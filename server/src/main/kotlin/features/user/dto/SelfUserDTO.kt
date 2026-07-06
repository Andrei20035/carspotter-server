package com.carspotter.features.user.dto

import com.carspotter.features.user.User
import com.carspotter.core.serialization.InstantSerializer
import com.carspotter.core.serialization.LocalDateSerializer
import com.carspotter.core.serialization.UUIDSerializer
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.*

private val CHANGE_COOLDOWN = Duration.ofDays(30)

@Serializable
data class SelfUserDTO(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val fullName: String,
    val profilePicturePath: String? = null,
    val username: String,
    val country: String,
    val phoneNumber: String? = null,
    @Serializable(with = LocalDateSerializer::class)
    val birthDate: LocalDate,
    val spotScore: Int = 0,
    val postCount: Int = 0,
    val isEarlySpotter: Boolean = false,
    val earlySpotterNumber: Int? = null,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant? = null,
    @Serializable(with = InstantSerializer::class)
    val updatedAt: Instant? = null,
    val streakDays: Int = 0,
    val canChangeFullName: Boolean = true,
    val canChangeCountry: Boolean = true,
    val canChangeBirthDate: Boolean = true,
    val canChangeUsername: Boolean = true,
    val canChangePhoneNumber: Boolean = true,
    @Serializable(with = InstantSerializer::class)
    val nextUsernameChangeAt: Instant? = null,
    @Serializable(with = InstantSerializer::class)
    val nextPhoneNumberChangeAt: Instant? = null,
)

fun User.toSelfDTO(profilePictureUrl: String? = profilePicturePath, postCount: Int = 0, streakDays: Int = 0): SelfUserDTO {
    val nextUsernameChangeAt = usernameChangedAt?.plus(CHANGE_COOLDOWN)
    val nextPhoneNumberChangeAt = phoneNumberChangedAt?.plus(CHANGE_COOLDOWN)
    val now = Instant.now()

    return SelfUserDTO(
        id = this.id,
        fullName = this.fullName,
        profilePicturePath = profilePictureUrl,
        username = this.username,
        country = this.country,
        phoneNumber = this.phoneNumber,
        birthDate = this.birthDate,
        spotScore = this.spotScore,
        postCount = postCount,
        isEarlySpotter = this.isEarlySpotter,
        earlySpotterNumber = this.earlySpotterNumber,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        streakDays = streakDays,
        canChangeFullName = fullNameChangedAt == null,
        canChangeCountry = countryChangedAt == null,
        canChangeBirthDate = birthDateChangedAt == null,
        canChangeUsername = nextUsernameChangeAt == null || nextUsernameChangeAt.isBefore(now),
        canChangePhoneNumber = nextPhoneNumberChangeAt == null || nextPhoneNumberChangeAt.isBefore(now),
        nextUsernameChangeAt = nextUsernameChangeAt,
        nextPhoneNumberChangeAt = nextPhoneNumberChangeAt,
    )
}
