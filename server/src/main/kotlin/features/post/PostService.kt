package com.carspotter.features.post

import com.carspotter.core.storage.IStorageService
import com.carspotter.features.car_model.ICarModelDAO
import com.carspotter.features.post.dto.CreatePostDTO
import com.carspotter.features.post.dto.FeedCursorDTO
import com.carspotter.features.post.dto.FeedResponseDTO
import com.carspotter.features.post.dto.PersistPostDTO
import com.carspotter.features.post.dto.PostDTO
import com.carspotter.features.post.dto.toDTO
import com.carspotter.features.post.dto.toFeedDTO
import features.comment.ICommentDAO
import features.like.ILikeDAO
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

interface IPostService {
    suspend fun createPost(request: CreatePostDTO): UUID
    suspend fun findPostById(postId: UUID): PostDTO?
    suspend fun listFeed(
        limit: Int,
        cursorCreatedAt: String?,
        cursorPostId: String?,
        currentUserId: UUID?,
    ): FeedResponseDTO
    suspend fun listPostsByUser(userId: UUID, limit: Int, offset: Long): List<PostDTO>
    suspend fun deletePostAsAuthor(postId: UUID, authorId: UUID)
}

class PostServiceImpl(
    private val postDao: IPostDAO,
    private val storageService: IStorageService,
    private val carModelDao: ICarModelDAO,
    private val likeDao: ILikeDAO,
    private val commentDao: ICommentDAO,
) : IPostService {
    companion object {
        private val logger = LoggerFactory.getLogger(PostServiceImpl::class.java)
        private val allowedContentTypes = setOf("image/jpeg", "image/png", "image/webp")
        private const val maxCaptionLength = 1_000
        private const val defaultLimit = 20
        private const val maxLimit = 100
        private val extensions = mapOf(
            "image/jpeg" to "jpg",
            "image/png" to "png",
            "image/webp" to "webp",
        )
    }

    override suspend fun createPost(request: CreatePostDTO): UUID {
        validateCreateRequest(request)

        val ext = extensions.getValue(request.contentType)
        val today = LocalDate.now()
        val objectKey = "posts/%04d/%02d/%02d/%s.%s".format(
            today.year,
            today.monthValue,
            today.dayOfMonth,
            UUID.randomUUID(),
            ext,
        )

        storageService.uploadImage(
            bytes = request.imageBytes,
            objectKey = objectKey,
            contentType = request.contentType,
        )

        return try {
            postDao.insert(
                PersistPostDTO(
                    userId = request.authorId,
                    carModelId = request.carModelId,
                    customBrand = request.customBrand?.trim(),
                    customModel = request.customModel?.trim(),
                    imageObjectKey = objectKey,
                    latitude = request.latitude,
                    longitude = request.longitude,
                    town = request.town?.trim()?.ifEmpty { null },
                    country = request.country?.trim()?.ifEmpty { null },
                    caption = request.caption?.trim(),
                )
            )
        } catch (e: Exception) {
            runCatching { storageService.deleteImage(objectKey) }
            throw PostCreationException("Failed to create post for user ${request.authorId}", e)
        }
    }

    override suspend fun findPostById(postId: UUID): PostDTO? =
        postDao.findById(postId)?.let(::toResponse)

    override suspend fun listFeed(
        limit: Int,
        cursorCreatedAt: String?,
        cursorPostId: String?,
        currentUserId: UUID?,
    ): FeedResponseDTO {
        val effectiveLimit = validateLimit(limit)
        val cursor = parseCursor(cursorCreatedAt, cursorPostId)

        // Fetch one extra row to determine whether another page exists.
        // An authenticated viewer never sees their own posts in the feed.
        val rows = postDao.listFeed(effectiveLimit + 1, cursor?.first, cursor?.second, excludeUserId = currentUserId)
        val hasMore = rows.size > effectiveLimit
        val page = if (hasMore) rows.take(effectiveLimit) else rows

        val postIds = page.map { it.id }
        val likeCounts = likeDao.getLikeCountsForPosts(postIds)
        val commentCounts = commentDao.getCommentCountsForPosts(postIds)
        val likedPostIds = if (currentUserId != null) {
            likeDao.getLikedPostIds(currentUserId, postIds)
        } else {
            emptySet()
        }

        val posts = page.map { post ->
            post.toFeedDTO(
                imageUrl = storageService.resolveUrl(post.imageKey),
                authorProfilePictureUrl = post.authorProfilePictureUrl?.let(storageService::resolveUrl),
                likeCount = likeCounts[post.id] ?: 0L,
                commentCount = commentCounts[post.id] ?: 0L,
                likedByCurrentUser = post.id in likedPostIds,
            )
        }

        val nextCursor = if (hasMore) {
            page.last().let { FeedCursorDTO(it.createdAt, it.id) }
        } else {
            null
        }

        return FeedResponseDTO(posts = posts, nextCursor = nextCursor, hasMore = hasMore)
    }

    /**
     * Parses the (created_at, id) cursor. Both parts must be present together or both omitted
     * (first page). Malformed values are rejected with [IllegalArgumentException].
     */
    private fun parseCursor(cursorCreatedAt: String?, cursorPostId: String?): Pair<Instant, UUID>? {
        if (cursorCreatedAt == null && cursorPostId == null) return null
        require(cursorCreatedAt != null && cursorPostId != null) {
            "Both cursorCreatedAt and cursorPostId must be provided together"
        }
        val createdAt = runCatching { Instant.parse(cursorCreatedAt) }
            .getOrElse { throw IllegalArgumentException("Invalid cursorCreatedAt") }
        val postId = runCatching { UUID.fromString(cursorPostId) }
            .getOrElse { throw IllegalArgumentException("Invalid cursorPostId") }
        return createdAt to postId
    }

    override suspend fun listPostsByUser(userId: UUID, limit: Int, offset: Long): List<PostDTO> =
        postDao.listByUser(userId, validateLimit(limit), validateOffset(offset)).map(::toResponse)

    override suspend fun deletePostAsAuthor(postId: UUID, authorId: UUID) {
        val post = postDao.findById(postId) ?: throw PostNotFoundException(postId)
        if (post.userId != authorId) {
            throw PostForbiddenException(postId, authorId)
        }

        postDao.deleteById(postId)
        runCatching { storageService.deleteImage(post.imageKey) }
            .onFailure { logger.warn("Post {} deleted but image cleanup failed", postId, it) }
    }

    private suspend fun validateCreateRequest(request: CreatePostDTO) {
        require(request.imageBytes.isNotEmpty()) { "Image is required" }
        require(request.contentType in allowedContentTypes) { "Unsupported image content type" }

        val caption = request.caption?.trim()
        require(caption == null || caption.isNotEmpty()) { "Caption cannot be blank" }
        require(caption == null || caption.length <= maxCaptionLength) {
            "Caption must be at most $maxCaptionLength characters"
        }

        val hasCarModelId = request.carModelId != null
        val brand = request.customBrand?.trim()
        val model = request.customModel?.trim()
        val hasCustomSource = !brand.isNullOrEmpty() || !model.isNullOrEmpty()

        require(hasCarModelId.xor(hasCustomSource)) {
            "Provide either carModelId or customBrand + customModel"
        }

        if (hasCarModelId) {
            require(brand == null && model == null) {
                "customBrand/customModel cannot be sent together with carModelId"
            }
            require(carModelDao.exists(request.carModelId!!)) { "carModelId does not exist" }
        } else {
            require(!brand.isNullOrEmpty() && !model.isNullOrEmpty()) {
                "customBrand and customModel are required when carModelId is missing"
            }
        }

        validateCoordinates(request.latitude, request.longitude)
    }

    private fun validateCoordinates(latitude: Double?, longitude: Double?) {
        require((latitude == null) == (longitude == null)) {
            "latitude and longitude must both be provided or both be omitted"
        }
        if (latitude != null && longitude != null) {
            require(latitude in -90.0..90.0) { "latitude must be between -90 and 90" }
            require(longitude in -180.0..180.0) { "longitude must be between -180 and 180" }
        }
    }

    private fun validateLimit(limit: Int): Int {
        if (limit == 0) {
            return defaultLimit
        }
        require(limit in 1..maxLimit) { "limit must be between 1 and $maxLimit" }
        return limit
    }

    private fun validateOffset(offset: Long): Long {
        require(offset >= 0) { "offset must be >= 0" }
        return offset
    }

    private fun toResponse(post: Post): PostDTO = post.toDTO(
        imageUrl = storageService.resolveUrl(post.imageKey),
        authorProfilePictureUrl = post.authorProfilePictureUrl?.let(storageService::resolveUrl),
    )
}

class PostCreationException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class PostNotFoundException(val postId: UUID) : RuntimeException("Post $postId not found")

class PostForbiddenException(postId: UUID, userId: UUID) :
    RuntimeException("User $userId cannot delete post $postId")
