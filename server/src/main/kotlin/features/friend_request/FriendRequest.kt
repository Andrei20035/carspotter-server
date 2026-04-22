package com.carspotter.features.friend_request

import java.time.Instant
import java.util.UUID

data class FriendRequest(
    val senderId: UUID,
    val receiverId: UUID,
    val createdAt: Instant? = null,
)