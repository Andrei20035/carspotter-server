package com.carspotter.features.post

import com.carspotter.features.post.dto.PersistPostDTO
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.notInList
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.*

interface IPostDAO {
    suspend fun createPost(persistPostDTO: PersistPostDTO): UUID
    suspend fun getPostById(postId: UUID): Post?
    suspend fun getAllPosts(): List<Post>
    suspend fun getCurrentDayPostsForUser(userId: UUID, startTime: Instant, endTime: Instant): List<Post>
    suspend fun editPost(postId: UUID, postText: String?): Int
    suspend fun deletePost(postId: UUID): Int
    suspend fun getUserIdByPost(postId: UUID): UUID
    suspend fun getFriendPosts(friendIds: List<UUID>, after: Instant? = null, limit: Int): List<Post>
    suspend fun getNearbyPosts(excludedIds: List<UUID>, lat: Double, lon: Double, radiusKm: Int, after: Instant? = null, limit: Int): List<Post>
    suspend fun getGlobalPosts(excludedIds: List<UUID>, after: Instant? = null, limit: Int): List<Post>
}

class PostDAO : IPostDAO {
    // PostDaoImpl.kt
    override suspend fun createPost(persistPostDTO: PersistPostDTO): UUID {
        return transaction {
            PostTable.insertReturning(listOf(PostTable.id)) {
                it[userId] = persistPostDTO.userId
                it[carModelId] = persistPostDTO.carModelId
                it[imagePath] = persistPostDTO.imageObjectKey
                it[description] = persistPostDTO.description
                it[longitude] = persistPostDTO.longitude
                it[latitude] = persistPostDTO.latitude
            }.singleOrNull()?.get(PostTable.id)?.value
                ?: throw IllegalStateException("Failed to insert post")
        }
    }

    override suspend fun getPostById(postId: UUID): Post? {
        return transaction {
            PostTable
                .selectAll()
                .where { PostTable.id eq postId }
                .mapNotNull { row -> row.toPost() }
                .singleOrNull()
        }
    }

    override suspend fun getAllPosts(): List<Post> {
        return transaction {
            PostTable
                .selectAll()
                .map { row -> row.toPost() }
        }
    }

    override suspend fun getCurrentDayPostsForUser(userId: UUID, startTime: Instant, endTime: Instant): List<Post> {
        return transaction {
            PostTable
                .selectAll()
                .where {
                    (PostTable.userId eq userId) and
                    (PostTable.createdAt greaterEq startTime) and
                    (PostTable.createdAt less endTime)
                }
                .map { row -> row.toPost() }
        }
    }


    override suspend fun editPost(postId: UUID, postText: String?): Int {
        return transaction {
            PostTable.update({ PostTable.id eq postId }) {
                it[description] = postText
                it[updatedAt] = Instant.now()
            }
        }
    }


    override suspend fun deletePost(postId: UUID): Int {
        return transaction {
            PostTable
                .deleteWhere { id eq postId }
        }
    }

    override suspend fun getUserIdByPost(postId: UUID): UUID {
        return transaction {
            PostTable
                .selectAll()
                .where { PostTable.id eq postId }
                .mapNotNull { row ->
                    row[PostTable.userId]
                }
                .singleOrNull() ?: throw IllegalArgumentException("Post with ID $postId not found")

        }
    }

    override suspend fun getFriendPosts(
        friendIds: List<UUID>,
        after: Instant?,
        limit: Int
    ): List<Post> {
        println("getFriendPosts called with: friendIds=$friendIds, after=$after, limit=$limit")
        return transaction {
            if (friendIds.isEmpty()) {
                println("No friend IDs provided")
                return@transaction emptyList()
            }

            val condition = if (after != null) {
                (PostTable.userId inList friendIds) and (PostTable.createdAt less after)
            } else {
                PostTable.userId inList friendIds
            }

            val posts = PostTable
                .selectAll()
                .where{ condition }
                .orderBy(PostTable.createdAt, SortOrder.DESC)
                .limit(limit)
                .map { it.toPost() }

            println("getFriendPosts returning ${posts.size} posts")
            return@transaction posts
        }
    }

    override suspend fun getNearbyPosts(
        excludedIds: List<UUID>,
        lat: Double,
        lon: Double,
        radiusKm: Int,
        after: Instant?,
        limit: Int
    ): List<Post> {
        println("getNearbyPosts called with: excludedIds=$excludedIds, lat=$lat, lon=$lon, radiusKm=$radiusKm, after=$after, limit=$limit")
        return transaction {
            val radiusDegrees = radiusKm / 111.0
            println("Calculated radius in degrees: $radiusDegrees")
            println("Search bounds: lat ${lat - radiusDegrees} to ${lat + radiusDegrees}, lon ${lon - radiusDegrees} to ${lon + radiusDegrees}")

            val condition = {
                (PostTable.userId notInList excludedIds) and
                        (PostTable.latitude greaterEq (lat - radiusDegrees)) and
                        (PostTable.latitude lessEq (lat + radiusDegrees)) and
                        (PostTable.longitude greaterEq (lon - radiusDegrees)) and
                        (PostTable.longitude lessEq (lon + radiusDegrees))
            }

            val baseQuery = PostTable
                .selectAll()
                .where { condition() }

            val filteredQuery = if (after != null) {
                baseQuery.andWhere { PostTable.createdAt less after }
            } else baseQuery

            val posts = filteredQuery
                .orderBy(PostTable.createdAt, SortOrder.DESC)
                .limit(limit)
                .map { it.toPost() }

            println("getNearbyPosts returning ${posts.size} posts")
            return@transaction posts
        }
    }

    override suspend fun getGlobalPosts(
        excludedIds: List<UUID>,
        after: Instant?,
        limit: Int
    ): List<Post> {
        println("getGlobalPosts called with: excludedIds=$excludedIds, after=$after, limit=$limit")
        return transaction {
            val baseCondition = PostTable.userId notInList excludedIds
            val condition = if (after != null) {
                baseCondition and (PostTable.createdAt less after)
            } else baseCondition

            val posts = PostTable
                .selectAll()
                .where { condition }
                .orderBy(PostTable.createdAt, SortOrder.DESC)
                .limit(limit)
                .map { it.toPost() }

            println("getGlobalPosts returning ${posts.size} posts")
            return@transaction posts
        }
    }
}