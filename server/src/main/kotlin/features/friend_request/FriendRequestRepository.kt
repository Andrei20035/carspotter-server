package com.carspotter.features.friend_request

import com.carspotter.features.user.User
import java.util.*

interface IFriendRequestRepository {
    suspend fun sendFriendRequest(senderId: UUID, receiverId: UUID): UUID
    suspend fun acceptFriendRequest(senderId: UUID, receiverId: UUID): Boolean
    suspend fun declineFriendRequest(senderId: UUID, receiverId: UUID): Int
    suspend fun getAllFriendRequests(userId: UUID): List<User>
    suspend fun getAllFriendReqFromDB(): List<FriendRequest>
}

class FriendRequestRepository(
    private val friendRequestDao: IFriendRequestDAO,
) : IFriendRequestRepository {
    override suspend fun sendFriendRequest(senderId: UUID, receiverId: UUID): UUID {
        return friendRequestDao.sendFriendRequest(senderId, receiverId)
    }

    override suspend fun acceptFriendRequest(senderId: UUID, receiverId: UUID): Boolean {
        return friendRequestDao.acceptFriendRequest(senderId, receiverId)
    }

    override suspend fun declineFriendRequest(senderId: UUID, receiverId: UUID): Int {
        return friendRequestDao.declineFriendRequest(senderId, receiverId)
    }

    override suspend fun getAllFriendRequests(userId: UUID): List<User> {
        return friendRequestDao.getAllFriendRequests(userId)
    }

    override suspend fun getAllFriendReqFromDB(): List<FriendRequest> {
        return friendRequestDao.getAllFriendReqFromDB()
    }
}