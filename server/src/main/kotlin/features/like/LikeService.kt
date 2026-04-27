package features.like

import org.jetbrains.exposed.exceptions.ExposedSQLException
import java.util.UUID

class LikePostNotFoundException(postId: UUID) : RuntimeException("Post $postId not found")

interface ILikeService {
    suspend fun toggleLike(userId: UUID, postId: UUID): LikeStatusDTO
    suspend fun getLikeStatus(postId: UUID, userId: UUID?): LikeStatusDTO
}

class LikeService(
    private val likeDao: ILikeDAO,
) : ILikeService {

    override suspend fun toggleLike(userId: UUID, postId: UUID): LikeStatusDTO {
        val alreadyLiked = likeDao.hasUserLikedPost(userId, postId)

        if (alreadyLiked) {
            likeDao.unlikePost(userId, postId)
        } else {
            try {
                likeDao.likePost(userId, postId)
            } catch (e: ExposedSQLException) {
                // sqlState 23503 = FK violation → postId nu există
                if (e.sqlState == "23503") throw LikePostNotFoundException(postId)
                throw e
            }
        }

        val count = likeDao.getLikeCount(postId)
        val liked = !alreadyLiked
        return LikeStatusDTO(liked = liked, count = count)
    }

    override suspend fun getLikeStatus(postId: UUID, userId: UUID?): LikeStatusDTO {
        val count = likeDao.getLikeCount(postId)
        val liked = userId?.let { likeDao.hasUserLikedPost(it, postId) } ?: false
        return LikeStatusDTO(liked = liked, count = count)
    }
}