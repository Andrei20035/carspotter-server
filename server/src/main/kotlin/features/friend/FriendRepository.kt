package com.carspotter.features.friend

import com.carspotter.features.user.User
import java.util.*

interface IFriendRepository {
    suspend fun addFriend(userId: UUID, friendId: UUID): UUID
    suspend fun getAllFriends(userId: UUID): List<User>
    suspend fun deleteFriend(userId: UUID, friendId: UUID): Int
    suspend fun getAllFriendsInDb(): List<Friend>
}

class FriendRepository(
    private val friendDao: IFriendDAO,
) : IFriendRepository {
    override suspend fun addFriend(userId: UUID, friendId: UUID): UUID {
        return friendDao.addFriend(userId, friendId)
    }

    override suspend fun getAllFriends(userId: UUID): List<User> {
        return friendDao.getAllFriends(userId)
    }

    override suspend fun deleteFriend(userId: UUID, friendId: UUID): Int {
        return friendDao.deleteFriend(userId, friendId)
    }

    override suspend fun getAllFriendsInDb(): List<Friend> {
        return friendDao.getAllFriendsInDb()
    }
}