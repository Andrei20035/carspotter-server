package com.carspotter.features.comment

import java.util.*

interface ICommentRepository {
    suspend fun addComment(userId: UUID, postId: UUID, commentText: String): UUID
    suspend fun deleteComment(commentId: UUID): Int
    suspend fun getCommentsForPost(postId: UUID): List<Comment>
    suspend fun getCommentById(commentId: UUID): Comment?
}

class CommentRepository(
    private val commentDao: ICommentDAO,
) : ICommentRepository {
    override suspend fun addComment(userId: UUID, postId: UUID, commentText: String): UUID {
        return commentDao.addComment(userId, postId, commentText)
    }

    override suspend fun deleteComment(commentId: UUID): Int {
        return commentDao.deleteComment(commentId)
    }

    override suspend fun getCommentsForPost(postId: UUID): List<Comment> {
        return commentDao.getCommentsForPost(postId)
    }

    override suspend fun getCommentById(commentId: UUID): Comment? {
        return commentDao.getCommentById(commentId)
    }
}