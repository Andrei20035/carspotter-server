package com.carspotter.features.comment

import com.carspotter.features.comment.dto.CommentDTO
import com.carspotter.features.comment.dto.toDTO
import java.util.*

interface ICommentService {
    suspend fun addComment(userId: UUID, postId: UUID, commentText: String): UUID
    suspend fun deleteComment(commentId: UUID): Int
    suspend fun getCommentsForPost(postId: UUID): List<CommentDTO>
    suspend fun getCommentById(commentId: UUID): CommentDTO?
}

class CommentService(
    private val commentRepository: ICommentRepository,
): ICommentService {
    override suspend fun addComment(userId: UUID, postId: UUID, commentText: String): UUID {
        return try {
            commentRepository.addComment(userId, postId, commentText)
        } catch (e: IllegalStateException) {
            throw IllegalArgumentException("Failed to add comment: $commentText", e)
        }
    }

    override suspend fun deleteComment(commentId: UUID): Int {
        return commentRepository.deleteComment(commentId)
    }

    override suspend fun getCommentsForPost(postId: UUID): List<CommentDTO> {
        return commentRepository.getCommentsForPost(postId).map { it.toDTO() }
    }

    override suspend fun getCommentById(commentId: UUID): CommentDTO? {
        return commentRepository.getCommentById(commentId)?.toDTO()
    }
}