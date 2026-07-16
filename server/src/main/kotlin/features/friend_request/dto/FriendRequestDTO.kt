package com.revio.server.features.friend_request.dto

import com.revio.server.features.friend_request.FriendRequest
import com.revio.server.core.serialization.InstantSerializer
import com.revio.server.core.serialization.UUIDSerializer
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.*

@Serializable
data class FriendRequestDTO(
    @Serializable(with = UUIDSerializer::class)
    val senderId: UUID,
    @Serializable(with = UUIDSerializer::class)
    val receiverId: UUID,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant? = null,
)

fun FriendRequest.toDTO() = FriendRequestDTO(
    senderId = this.senderId,
    receiverId = this.receiverId,
    createdAt = this.createdAt
)
