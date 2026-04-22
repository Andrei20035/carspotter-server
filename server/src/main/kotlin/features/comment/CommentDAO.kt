package com.carspotter.features.comment

import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertReturning
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

interface ICommentDAO {
    suspend fun addComment(userId: UUID, postId: UUID, commentText: String): UUID
    suspend fun deleteComment(commentId: UUID): Int
    suspend fun getCommentsForPost(postId: UUID): List<Comment>
    suspend fun getCommentById(commentId: UUID): Comment?
}

class CommentDAO : ICommentDAO {
    override suspend fun addComment(userId: UUID, postId: UUID, commentText: String): UUID {
        return transaction {
            CommentTable
                .insertReturning(listOf(CommentTable.id)) {
                    it[CommentTable.userId] = userId
                    it[CommentTable.postId] = postId
                    it[CommentTable.commentText] = commentText
                }.singleOrNull()?.get(CommentTable.id)?.value ?: throw IllegalStateException("Failed to insert comment")
        }
    }

    override suspend fun deleteComment(commentId: UUID): Int {
        return transaction {
            CommentTable
                .deleteWhere { id eq commentId }
        }
    }

    override suspend fun getCommentsForPost(postId: UUID): List<Comment> {
        return transaction {
            CommentTable
                .selectAll()
                .where { CommentTable.postId eq postId }
                .mapNotNull { row ->
                    Comment(
                        id = row[CommentTable.id].value,
                        userId = row[CommentTable.userId],
                        postId = row[CommentTable.postId],
                        commentText = row[CommentTable.commentText],
                        createdAt = row[CommentTable.createdAt],
                        updatedAt = row[CommentTable.updatedAt],
                    )
                }
        }
    }

    override suspend fun getCommentById(commentId: UUID): Comment? {
        return transaction {
            CommentTable
                .selectAll()
                .where { CommentTable.id eq commentId }
                .mapNotNull { row ->
                    Comment(
                        id = row[CommentTable.id].value,
                        userId = row[CommentTable.userId],
                        postId = row[CommentTable.postId],
                        commentText = row[CommentTable.commentText],
                    )
                }.singleOrNull()
        }
    }

}