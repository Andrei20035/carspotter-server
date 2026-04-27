package features.comment

import java.time.Instant
import java.util.UUID

data class Comment(
    val id: UUID = UUID.randomUUID(),
    val userId: UUID,
    val postId: UUID,
    val username: String,
    val profilePicturePath: String?,
    val commentText: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)