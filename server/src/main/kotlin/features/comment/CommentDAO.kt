package features.comment

import com.carspotter.features.comment.CommentTable
import com.carspotter.features.user.UserTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

interface ICommentDAO {
    suspend fun addComment(userId: UUID, postId: UUID, commentText: String): Comment
    suspend fun deleteComment(commentId: UUID): Int
    suspend fun getCommentsForPost(postId: UUID): List<Comment>
    suspend fun getCommentById(commentId: UUID): Comment?

    /** Batched comment counts for a set of posts. Returns a map postId -> count (posts with no comments are absent). */
    suspend fun getCommentCountsForPosts(postIds: List<UUID>): Map<UUID, Long>
}

class CommentDAO : ICommentDAO {

    /** Coloane pentru join cu users — selectate o singură dată ca să evităm tipare. */
    private val joinedColumns = listOf(
        CommentTable.id,
        CommentTable.userId,
        CommentTable.postId,
        CommentTable.commentText,
        CommentTable.createdAt,
        CommentTable.updatedAt,
        UserTable.username,
        UserTable.profilePicturePath,
    )

    private fun ResultRow.toComment(): Comment = Comment(
        id = this[CommentTable.id].value,
        userId = this[CommentTable.userId],
        postId = this[CommentTable.postId],
        commentText = this[CommentTable.commentText],
        username = this[UserTable.username],
        profilePicturePath = this[UserTable.profilePicturePath],
        createdAt = this[CommentTable.createdAt],
        updatedAt = this[CommentTable.updatedAt],
    )

    override suspend fun addComment(userId: UUID, postId: UUID, commentText: String): Comment = transaction {
        val newId = CommentTable
            .insertReturning(listOf(CommentTable.id)) {
                it[CommentTable.userId] = userId
                it[CommentTable.postId] = postId
                it[CommentTable.commentText] = commentText
            }.singleOrNull()?.get(CommentTable.id)?.value
            ?: error("INSERT did not return id")

        // Citește înapoi cu join pentru a popula username/avatar
        (CommentTable innerJoin UserTable)
            .select(joinedColumns)
            .where { CommentTable.id eq newId }
            .single()
            .toComment()
    }

    override suspend fun deleteComment(commentId: UUID): Int = transaction {
        CommentTable.deleteWhere { id eq commentId }
    }

    override suspend fun getCommentsForPost(postId: UUID): List<Comment> = transaction {
        (CommentTable innerJoin UserTable)
            .select(joinedColumns)
            .where { CommentTable.postId eq postId }
            .orderBy(CommentTable.createdAt to SortOrder.ASC)
            .map { it.toComment() }
    }

    override suspend fun getCommentById(commentId: UUID): Comment? = transaction {
        (CommentTable innerJoin UserTable)
            .select(joinedColumns)
            .where { CommentTable.id eq commentId }
            .singleOrNull()
            ?.toComment()
    }

    override suspend fun getCommentCountsForPosts(postIds: List<UUID>): Map<UUID, Long> = transaction {
        if (postIds.isEmpty()) return@transaction emptyMap()
        val countExpr = CommentTable.id.count()
        CommentTable
            .select(CommentTable.postId, countExpr)
            .where { CommentTable.postId inList postIds }
            .groupBy(CommentTable.postId)
            .associate { it[CommentTable.postId] to it[countExpr] }
    }
}