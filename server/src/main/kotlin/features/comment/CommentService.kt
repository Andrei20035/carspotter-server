package features.comment

import com.carspotter.core.storage.IStorageService
import com.carspotter.features.comment.dto.CommentDTO
import com.carspotter.features.comment.dto.toDTO
import com.carspotter.features.post.IPostDAO
import com.carspotter.features.scoring.IScoringService
import org.jetbrains.exposed.exceptions.ExposedSQLException
import java.util.UUID

class PostNotFoundException(postId: UUID) : RuntimeException("Post $postId does not exist")
class CommentNotFoundException(commentId: UUID) : RuntimeException("Comment $commentId not found")
class CommentForbiddenException : RuntimeException("Not authorized to delete this comment")
class CommentValidationException(msg: String) : RuntimeException(msg)

interface ICommentService {
    suspend fun addComment(userId: UUID, postId: UUID, commentText: String): CommentDTO
    suspend fun deleteComment(commentId: UUID, requesterId: UUID)
    suspend fun getCommentsForPost(postId: UUID): List<CommentDTO>
}

class CommentService(
    private val commentDao: ICommentDAO,
    private val storageService: IStorageService,
    private val postDao: IPostDAO,
    private val scoringService: IScoringService,
) : ICommentService {

    companion object {
        const val MAX_COMMENT_LENGTH = 1000
    }

    override suspend fun addComment(userId: UUID, postId: UUID, commentText: String): CommentDTO {
        val text = commentText.trim()
        if (text.isBlank()) throw CommentValidationException("Comment text cannot be blank")
        if (text.length > MAX_COMMENT_LENGTH) {
            throw CommentValidationException("Comment text exceeds $MAX_COMMENT_LENGTH characters")
        }

        // Check before inserting: is this user's first comment on this post?
        val isFirstComment = !commentDao.hasUserCommentedOnPost(userId, postId)
        val ownerInfo = postDao.getOwnerAndSource(postId)

        return try {
            val comment = commentDao.addComment(userId, postId, text).toResponse()
            // Award first-commenter points if this is the user's first comment and not a self-comment.
            if (isFirstComment && ownerInfo != null && ownerInfo.ownerId != userId) {
                scoringService.onFirstCommentByUser(
                    postOwnerId = ownerInfo.ownerId,
                    postId = postId,
                    commenterId = userId,
                    source = ownerInfo.source,
                )
            }
            comment
        } catch (e: ExposedSQLException) {
            if (e.sqlState == "23503") throw PostNotFoundException(postId)
            throw e
        }
    }

    override suspend fun deleteComment(commentId: UUID, requesterId: UUID) {
        val comment = commentDao.getCommentById(commentId)
            ?: throw CommentNotFoundException(commentId)
        if (comment.userId != requesterId) throw CommentForbiddenException()
        commentDao.deleteComment(commentId)
    }

    override suspend fun getCommentsForPost(postId: UUID): List<CommentDTO> =
        commentDao.getCommentsForPost(postId).map { it.toResponse() }

    private fun Comment.toResponse(): CommentDTO = toDTO(
        profilePictureUrl = profilePicturePath?.let(storageService::resolveUrl),
    )
}
