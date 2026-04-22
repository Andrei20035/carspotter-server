package com.carspotter.features.post

import com.carspotter.core.serialization.InstantSerializer
import com.carspotter.core.serialization.UUIDSerializer
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.ResultRow
import java.time.Instant
import java.util.*

@Serializable
data class FeedCursor(
    @Serializable(with = InstantSerializer::class)
    val lastCreatedAt: Instant,
    @Serializable(with = UUIDSerializer::class)
    val lastPostId: UUID
)

enum class FeedStage {
    FRIENDS, NEARBY, GLOBAL
}

data class Post(
    val id: UUID = UUID.randomUUID(),
    val userId: UUID,
    val carModelId: UUID,
    val imagePath: String,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val description: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)

fun ResultRow.toPost(): Post {
    return Post(
        id = this[PostTable.id].value,
        userId = this[PostTable.userId],
        carModelId = this[PostTable.carModelId],
        imagePath = this[PostTable.imagePath],
        description = this[PostTable.description],
        latitude = this[PostTable.latitude],
        longitude = this[PostTable.longitude],
        createdAt = this[PostTable.createdAt],
        updatedAt = this[PostTable.updatedAt]
    )
}

fun Post.toCursor(): FeedCursor {
    return FeedCursor(
        lastCreatedAt = this.createdAt,
        lastPostId = this.id,
    )
}