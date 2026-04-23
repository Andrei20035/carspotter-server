package com.carspotter.features.like

import com.carspotter.features.user.dto.UserDTO
import com.carspotter.features.comment.dto.toDTO
import com.carspotter.features.user.dto.toDTO
import java.util.*

interface ILikeService {
    suspend fun likePost(userId: UUID, postId: UUID): UUID
    suspend fun unlikePost(userId: UUID, postId: UUID): Int
    suspend fun getLikesForPost(postId: UUID): List<UserDTO>
}

class LikeServiceImpl(
    private val likeRepository: ILikeRepository
): ILikeService {
    override suspend fun likePost(userId: UUID, postId: UUID): UUID {
        val exists = likeRepository.hasUserLikedPost(userId, postId)

        if (exists) {
            throw DuplicateLikeException("User $userId has already liked post $postId")
        }

        return try {
            likeRepository.likePost(userId, postId)
        } catch (e: IllegalStateException) {
            throw LikeCreationException("Failed to like post $postId for user $userId", e)
        }
    }

    override suspend fun unlikePost(userId: UUID, postId: UUID): Int {
        val exists = likeRepository.hasUserLikedPost(userId, postId)
        if(!exists) return 0
        return likeRepository.unlikePost(userId, postId)
    }

    override suspend fun getLikesForPost(postId: UUID): List<UserDTO> {
        return likeRepository.getLikesForPost(postId).map { it.toDTO() }
    }
}

class LikeCreationException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
class DuplicateLikeException(message: String, cause: Throwable? = null) : Exception(message, cause)