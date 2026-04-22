package com.carspotter.features.friend

import java.time.Instant
import java.util.UUID

data class Friend(
    val userId: UUID,
    val friendId: UUID,
    val createdAt: Instant? = null
)