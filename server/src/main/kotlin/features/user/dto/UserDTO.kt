package com.carspotter.features.user.dto

import com.carspotter.features.user.User
import com.carspotter.core.serialization.InstantSerializer
import com.carspotter.core.serialization.UUIDSerializer
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.*

@Serializable
data class UserDTO(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val fullName: String,
    val profilePicturePath: String? = null,
    val username: String,
    val country: String,
    val spotScore: Int = 0,
    val postCount: Int = 0,
    val isEarlySpotter: Boolean = false,
    val earlySpotterNumber: Int? = null,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant? = null,
    @Serializable(with = InstantSerializer::class)
    val updatedAt: Instant? = null
)

fun User.toDTO(profilePictureUrl: String? = profilePicturePath, postCount: Int = 0): UserDTO {
    return UserDTO(
        id = this.id,
        fullName = this.fullName,
        profilePicturePath = profilePictureUrl,
        username = this.username,
        country = this.country,
        spotScore = this.spotScore,
        postCount = postCount,
        isEarlySpotter = this.isEarlySpotter,
        earlySpotterNumber = this.earlySpotterNumber,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt
    )
}
