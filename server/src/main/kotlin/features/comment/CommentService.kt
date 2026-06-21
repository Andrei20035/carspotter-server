package features.comment

import com.carspotter.core.storage.IStorageService
import com.carspotter.features.comment.dto.CommentDTO
import com.carspotter.features.comment.dto.toDTO
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

        return try {
            commentDao.addComment(userId, postId, text).toResponse()
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
