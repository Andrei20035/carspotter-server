package com.revio.server.features.friend_request

import com.revio.server.features.friend_request.dto.FriendRequestDTO
import com.revio.server.features.user.dto.UserDTO
import com.revio.server.features.comment.dto.toDTO
import com.revio.server.features.friend_request.dto.toDTO
import com.revio.server.features.user.dto.toDTO
import java.util.*

interface IFriendRequestService {
    suspend fun sendFriendRequest(senderId: UUID, receiverId: UUID): UUID
    suspend fun acceptFriendRequest(senderId: UUID, receiverId: UUID): Boolean
    suspend fun declineFriendRequest(senderId: UUID, receiverId: UUID): Int
    suspend fun getAllFriendRequests(userId: UUID): List<UserDTO>
    suspend fun getAllFriendReqFromDB(): List<FriendRequestDTO>
}

class FriendRequestServiceImpl(
    private val friendRequestRepository: IFriendRequestRepository
): IFriendRequestService {
    override suspend fun sendFriendRequest(senderId: UUID, receiverId: UUID): UUID {
        try {
            if(senderId == receiverId) throw IllegalArgumentException("Cannot send friend request to yourself")

            return friendRequestRepository.sendFriendRequest(senderId, receiverId)
        } catch(e: IllegalStateException) {
            throw SendFriendRequestException("Failed to send friend request: $senderId <-> $receiverId", e)
        }
    }

    override suspend fun acceptFriendRequest(senderId: UUID, receiverId: UUID): Boolean {
        return friendRequestRepository.acceptFriendRequest(senderId, receiverId)
    }

    override suspend fun declineFriendRequest(senderId: UUID, receiverId: UUID): Int {
        return friendRequestRepository.declineFriendRequest(senderId, receiverId)
    }

    override suspend fun getAllFriendRequests(userId: UUID): List<UserDTO> {
        return friendRequestRepository.getAllFriendRequests(userId).map { it.toDTO() }
    }

    override suspend fun getAllFriendReqFromDB(): List<FriendRequestDTO> {
        return friendRequestRepository.getAllFriendReqFromDB().map { it.toDTO() }
    }
}

class SendFriendRequestException(message: String, error: Throwable? = null): Exception(message, error)