package com.carspotter.features.friend.dto

import com.carspotter.features.friend.Friend
import com.carspotter.core.serialization.InstantSerializer
import com.carspotter.core.serialization.UUIDSerializer
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.*

@Serializable
data class FriendDTO(
    @Serializable(with = UUIDSerializer::class)
    val userId: UUID,
    @Serializable(with = UUIDSerializer::class)
    val friendId: UUID,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant? = null
)

fun Friend.toDTO() = FriendDTO(
    userId = this.userId,
    friendId = this.friendId,
    createdAt = this.createdAt
)
