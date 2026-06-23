package features.like

import com.carspotter.features.post.IPostDAO
import com.carspotter.features.scoring.IScoringService
import org.jetbrains.exposed.exceptions.ExposedSQLException
import java.util.UUID

class LikePostNotFoundException(postId: UUID) : RuntimeException("Post $postId not found")

interface ILikeService {
    suspend fun toggleLike(userId: UUID, postId: UUID): LikeStatusDTO
    suspend fun getLikeStatus(postId: UUID, userId: UUID?): LikeStatusDTO
}

class LikeService(
    private val likeDao: ILikeDAO,
    private val postDao: IPostDAO,
    private val scoringService: IScoringService,
) : ILikeService {

    override suspend fun toggleLike(userId: UUID, postId: UUID): LikeStatusDTO {
        val alreadyLiked = likeDao.hasUserLikedPost(userId, postId)
        val ownerInfo = postDao.getOwnerAndSource(postId) ?: throw LikePostNotFoundException(postId)

        if (alreadyLiked) {
            likeDao.unlikePost(userId, postId)
            scoringService.onPostUnliked(
                postOwnerId = ownerInfo.ownerId,
                postId = postId,
                unlikerId = userId,
                source = ownerInfo.source,
            )
        } else {
            try {
                likeDao.likePost(userId, postId)
            } catch (e: ExposedSQLException) {
                // sqlState 23503 = FK violation → postId doesn't exist
                if (e.sqlState == "23503") throw LikePostNotFoundException(postId)
                throw e
            }
            scoringService.onPostLiked(
                postOwnerId = ownerInfo.ownerId,
                postId = postId,
                likerId = userId,
                source = ownerInfo.source,
            )
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
