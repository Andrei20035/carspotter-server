package com.carspotter.features.user.dto

import com.carspotter.features.user.User
import com.carspotter.core.serialization.LocalDateSerializer
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.util.*

@Serializable
data class CreateUserRequest(
    val profilePicturePath: String? = null,
    val fullName: String,
    val phoneNumber: String? = null,
    @Serializable(with = LocalDateSerializer::class)
    val birthDate: LocalDate,
    val username: String,
    val country: String
)

fun CreateUserRequest.toUser(credentialId: UUID) = User(
    authCredentialId = credentialId,
    profilePicturePath = this.profilePicturePath,
    fullName = this.fullName,
    phoneNumber = this.phoneNumber,
    birthDate = this.birthDate,
    username = this.username,
    country = this.country,
)
