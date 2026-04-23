package com.carspotter.features.post

import com.carspotter.features.friend.IFriendDAO
import com.carspotter.features.post.dto.PersistPostDTO
import com.carspotter.features.post.dto.FeedResponse
import com.carspotter.features.comment.dto.toDTO
import com.carspotter.features.post.dto.toDTO
import java.time.Instant
import java.util.*

interface IPostRepository {
    suspend fun createPost(persistPostDTO: PersistPostDTO): UUID
    suspend fun getPostById(postId: UUID): Post?
    suspend fun getAllPosts(): List<Post>
    suspend fun getCurrentDayPostsForUser(userId: UUID, startOfDay: Instant, endOfDay: Instant): List<Post>
    suspend fun editPost(postId: UUID, postText: String?): Int
    suspend fun deletePost(postId: UUID): Int
    suspend fun getUserIdByPost(postId: UUID): UUID
    suspend fun getFeedPostsForUser(userId: UUID, latitude: Double?, longitude: Double?, radiusKm: Int?, limit: Int, cursor: FeedCursor?): FeedResponse
}

class PostRepository(
    private val postDao: IPostDAO,
    private val friendDao: IFriendDAO,
) : IPostRepository {
    override suspend fun createPost(persistPostDTO: PersistPostDTO): UUID {
        return postDao.createPost(persistPostDTO)
    }

    override suspend fun getPostById(postId: UUID): Post? {
        return postDao.getPostById(postId)
    }

    override suspend fun getAllPosts(): List<Post> {
        return postDao.getAllPosts()
    }

    override suspend fun getCurrentDayPostsForUser(userId: UUID, startOfDay: Instant, endOfDay: Instant): List<Post> {
        return postDao.getCurrentDayPostsForUser(userId, startOfDay, endOfDay)
    }


    override suspend fun editPost(postId: UUID, postText: String?): Int {
        return postDao.editPost(postId, postText)
    }

    override suspend fun deletePost(postId: UUID): Int {
        return postDao.deletePost(postId)
    }

    override suspend fun getUserIdByPost(postId: UUID): UUID {
        return postDao.getUserIdByPost(postId)
    }

    override suspend fun getFeedPostsForUser(
        userId: UUID,
        latitude: Double?,
        longitude: Double?,
        radiusKm: Int?,
        limit: Int,
        cursor: FeedCursor?
    ): FeedResponse {
        val friendsIds = friendDao.getFriendIdsForUser(userId)
        val collectedPosts = mutableListOf<Post>()
        val collectedUserIds = mutableSetOf<UUID>() // Track users we've already collected posts from
        var remaining = limit
        var lastCursor: FeedCursor? = cursor

        // 1. Friend posts first
        if (remaining > 0) {
            val friendPosts = postDao.getFriendPosts(friendsIds, cursor?.lastCreatedAt, remaining)
            collectedPosts.addAll(friendPosts)
            collectedUserIds.addAll(friendPosts.map { it.userId }) // Track collected users
            remaining -= friendPosts.size

            if (friendPosts.isNotEmpty()) {
                lastCursor = friendPosts.last().toCursor()
            }
        }

        // 2. Nearby posts second
        if (remaining > 0 && latitude != null && longitude != null && radiusKm != null) {
            val nearbyPosts = postDao.getNearbyPosts(
                friendsIds + userId + collectedUserIds.toList(), // Exclude already collected users
                latitude,
                longitude,
                radiusKm,
                cursor?.lastCreatedAt,
                remaining
            )
            collectedPosts.addAll(nearbyPosts)
            collectedUserIds.addAll(nearbyPosts.map { it.userId }) // Track collected users
            remaining -= nearbyPosts.size

            if (nearbyPosts.isNotEmpty()) {
                lastCursor = nearbyPosts.last().toCursor()
            }
        }

        // 3. Global posts last
        if (remaining > 0) {
            val globalPosts = postDao.getGlobalPosts(
                friendsIds + userId + collectedUserIds.toList(), // Exclude already collected users
                cursor?.lastCreatedAt,
                remaining
            )
            collectedPosts.addAll(globalPosts)

            if (globalPosts.isNotEmpty()) {
                lastCursor = globalPosts.last().toCursor()
            }
        }

        val hasMore = collectedPosts.size == limit

        return FeedResponse(
            posts = collectedPosts.toDTO(),
            nextCursor = if (hasMore) lastCursor else null,
            hasMore = hasMore
        )
    }
}