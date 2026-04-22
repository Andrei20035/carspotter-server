package com.carspotter.features.like

import com.carspotter.features.user.User
import java.util.*

interface ILikeRepository {
    suspend fun likePost(userId: UUID, postId: UUID): UUID
    suspend fun unlikePost(userId: UUID, postId: UUID): Int
    suspend fun getLikesForPost(postId: UUID): List<User>
    suspend fun hasUserLikedPost(userId: UUID, postId: UUID): Boolean
}

class LikeRepository(
    private val likeDao: ILikeDAO,
) : ILikeRepository {
    override suspend fun likePost(userId: UUID, postId: UUID): UUID {
        return likeDao.likePost(userId, postId)
    }

    override suspend fun unlikePost(userId: UUID, postId: UUID): Int {
        return likeDao.unlikePost(userId, postId)
    }

    override suspend fun getLikesForPost(postId: UUID): List<User> {
        return likeDao.getLikesForPost(postId)
    }

    override suspend fun hasUserLikedPost(userId: UUID, postId: UUID): Boolean {
        return likeDao.hasUserLikedPost(userId, postId)
    }
}