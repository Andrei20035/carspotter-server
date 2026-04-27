package com.carspotter.features.post

import com.carspotter.features.car_model.CarModelTable
import com.carspotter.features.post.dto.PersistPostDTO
import com.carspotter.features.user.UserTable
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertReturning
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.util.UUID

interface IPostDAO {
    suspend fun insert(post: PersistPostDTO): UUID
    suspend fun findById(postId: UUID): Post?
    suspend fun listFeed(limit: Int, offset: Long): List<Post>
    suspend fun listByUser(userId: UUID, limit: Int, offset: Long): List<Post>
    suspend fun deleteById(postId: UUID): Int
}

class PostDAO : IPostDAO {
    private val joinedColumns = listOf(
        PostTable.id,
        PostTable.userId,
        UserTable.username,
        PostTable.carModelId,
        CarModelTable.brand,
        CarModelTable.model,
        PostTable.customBrand,
        PostTable.customModel,
        PostTable.imageKey,
        PostTable.caption,
        PostTable.latitude,
        PostTable.longitude,
        PostTable.createdAt,
    )

    private fun baseQuery() = PostTable
        .join(UserTable, JoinType.INNER, additionalConstraint = { PostTable.userId eq UserTable.id })
        .join(CarModelTable, JoinType.LEFT, additionalConstraint = { PostTable.carModelId eq CarModelTable.id })
        .select(joinedColumns)

    override suspend fun insert(post: PersistPostDTO): UUID = transaction {
        PostTable.insertReturning(listOf(PostTable.id)) {
            it[userId] = post.userId
            it[carModelId] = post.carModelId
            it[customBrand] = post.customBrand
            it[customModel] = post.customModel
            it[imageKey] = post.imageObjectKey
            it[caption] = post.caption
            it[latitude] = post.latitude
            it[longitude] = post.longitude
        }.singleOrNull()?.get(PostTable.id)?.value
            ?: error("Failed to insert post")
    }

    override suspend fun findById(postId: UUID): Post? = transaction {
        baseQuery()
            .where { PostTable.id eq postId }
            .singleOrNull()
            ?.toPost()
    }

    override suspend fun listFeed(limit: Int, offset: Long): List<Post> = transaction {
        baseQuery()
            .orderBy(PostTable.createdAt to SortOrder.DESC, PostTable.id to SortOrder.DESC)
            .limit(limit)
            .offset(offset)
            .map(ResultRow::toPost)
    }

    override suspend fun listByUser(userId: UUID, limit: Int, offset: Long): List<Post> = transaction {
        baseQuery()
            .where { PostTable.userId eq userId }
            .orderBy(PostTable.createdAt to SortOrder.DESC, PostTable.id to SortOrder.DESC)
            .limit(limit)
            .offset(offset)
            .map(ResultRow::toPost)
    }

    override suspend fun deleteById(postId: UUID): Int = transaction {
        PostTable.deleteWhere { id eq postId }
    }
}
