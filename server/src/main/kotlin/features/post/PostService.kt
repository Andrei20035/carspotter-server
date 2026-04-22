package com.carspotter.features.post

import com.carspotter.features.post.dto.CreatePostDTO
import com.carspotter.features.post.dto.PersistPostDTO
import com.carspotter.features.post.dto.PostDTO
import com.carspotter.features.post.dto.FeedResponse
import com.carspotter.features.comment.dto.toDTO
import com.carspotter.core.storage.IStorageService
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*

interface IPostService {
    suspend fun createPost(createPostDTO: CreatePostDTO): UUID
    suspend fun getPostById(postId: UUID): PostDTO?
    suspend fun getAllPosts(): List<PostDTO>
    suspend fun getCurrentDayPostsForUser(userId: UUID, userTimeZone: ZoneId): List<PostDTO>
    suspend fun editPost(postId: UUID, postText: String?): Int
    suspend fun deletePost(postId: UUID): Int
    suspend fun getUserIdByPost(postId: UUID): UUID
    suspend fun getFeedPostsForUser(userId: UUID, latitude: Double?, longitude: Double?, radiusKm: Int?, limit: Int, cursor: FeedCursor?): FeedResponse

}

class PostServiceImpl(
    private val postRepository: IPostRepository,
    private val storageService: IStorageService

): IPostService {
    companion object {
        private val ALLOWED_CONTENT_TYPES = setOf("image/jpeg", "image/png", "image/webp")
        private val EXTENSIONS = mapOf(
            "image/jpeg" to "jpg",
            "image/png"  to "png",
            "image/webp" to "webp"
        )
    }

    override suspend fun createPost(createPostDTO: CreatePostDTO): UUID {
        require(createPostDTO.contentType in ALLOWED_CONTENT_TYPES) {
            "Unsupported content type: ${createPostDTO.contentType}"
        }

        val ext = EXTENSIONS.getValue(createPostDTO.contentType)
        val today = LocalDate.now()
        val objectKey = "posts/%04d/%02d/%02d/%s.%s".format(
            today.year, today.monthValue, today.dayOfMonth,
            UUID.randomUUID(), ext
        )

        storageService.uploadImage(
            bytes = createPostDTO.imageBytes,
            objectKey = objectKey,
            contentType = createPostDTO.contentType
        )

        return try {
            postRepository.createPost(
                PersistPostDTO(
                    userId = createPostDTO.userId,
                    carModelId = createPostDTO.carModelId,
                    imageObjectKey = objectKey,
                    latitude = createPostDTO.latitude,
                    longitude = createPostDTO.longitude,
                    description = createPostDTO.description
                )
            )
        } catch (e: Exception) {
            runCatching { storageService.deleteImage(objectKey) }
            throw PostCreationException("Failed to create post for user ${createPostDTO.userId}", e)
        }
    }

    override suspend fun getPostById(postId: UUID): PostDTO? {
        return postRepository.getPostById(postId)?.toDTO()
    }

    override suspend fun getAllPosts(): List<PostDTO> {
        return postRepository.getAllPosts().map { it.toDTO() }
    }

    override suspend fun getCurrentDayPostsForUser(userId: UUID, userTimeZone: ZoneId): List<PostDTO> {
        val nowInUserTimeZone = ZonedDateTime.now(userTimeZone)
        val startOfDay = nowInUserTimeZone.toLocalDate().atStartOfDay(userTimeZone).toInstant()
        val endOfDay = nowInUserTimeZone.toLocalDate().atTime(23, 59, 59).atZone(userTimeZone).toInstant()

        val posts = postRepository.getCurrentDayPostsForUser(userId, startOfDay, endOfDay)

        return posts.map { it.toDTO() }
    }

    override suspend fun editPost(postId: UUID, postText: String?): Int {
        return postRepository.editPost(postId, postText)
    }

    override suspend fun deletePost(postId: UUID): Int {
        return postRepository.deletePost(postId)
    }

    override suspend fun getUserIdByPost(postId: UUID): UUID {
        return try {
            postRepository.getUserIdByPost(postId)
        } catch (e: IllegalStateException) {
            throw IllegalArgumentException("Cannot fetch user: post does not exist", e)
        }
    }

    override suspend fun getFeedPostsForUser(
        userId: UUID,
        latitude: Double?,
        longitude: Double?,
        radiusKm: Int?,
        limit: Int,
        cursor: FeedCursor?
    ): FeedResponse {
        return postRepository.getFeedPostsForUser(userId, latitude, longitude, radiusKm, limit, cursor)
    }
}

class PostCreationException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)