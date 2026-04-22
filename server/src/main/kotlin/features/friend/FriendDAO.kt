package com.carspotter.features.friend

import com.carspotter.features.user.User
import com.carspotter.features.user.UserTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

interface IFriendDAO {
    suspend fun addFriend(userId: UUID, friendId: UUID): UUID
    suspend fun getAllFriends(userId: UUID): List<User>
    suspend fun deleteFriend(userId: UUID, friendId: UUID): Int
    suspend fun getAllFriendsInDb(): List<Friend>
    suspend fun getFriendIdsForUser(userId: UUID): List<UUID>
}

class FriendDAO : IFriendDAO {
    override suspend fun addFriend(userId: UUID, friendId: UUID): UUID {
        return transaction {
            val primaryInsert = insertFriend(userId, friendId)
            val secondaryInsert = insertFriend(friendId, userId)

            if (primaryInsert == null || secondaryInsert == null) {
                throw IllegalStateException("Failed to insert friendship: $userId <-> $friendId")
            }
            friendId
        }
    }

    private fun insertFriend(userId: UUID, friendId: UUID): UUID? {
        return FriendTable.insertReturning(listOf(FriendTable.friendId)) {
            it[FriendTable.userId] = userId
            it[FriendTable.friendId] = friendId
        }.singleOrNull()?.get(FriendTable.friendId)
    }

    override suspend fun deleteFriend(userId: UUID, friendId: UUID): Int {
        return transaction {
            FriendTable.deleteWhere {
                ((FriendTable.userId eq userId) and (FriendTable.friendId eq friendId)) or
                        ((FriendTable.userId eq friendId) and (FriendTable.friendId eq userId))
            }
        }
    }


    override suspend fun getAllFriends(userId: UUID): List<User> = transaction {
        val usersAlias = UserTable.alias("u")
        FriendTable
            .join(usersAlias, JoinType.INNER, additionalConstraint = { FriendTable.friendId eq usersAlias[UserTable.id] })
            .selectAll()
            .where { FriendTable.userId eq userId}
            .map { row ->
                User(
                    id = row[usersAlias[UserTable.id]].value,
                    authCredentialId = row[usersAlias[UserTable.authCredentialId]],
                    profilePicturePath = row[usersAlias[UserTable.profilePicturePath]],
                    fullName = row[usersAlias[UserTable.fullName]],
                    phoneNumber = row[usersAlias[UserTable.phoneNumber]],
                    birthDate = row[usersAlias[UserTable.birthDate]],
                    username = row[usersAlias[UserTable.username]],
                    country = row[usersAlias[UserTable.country]],
                    spotScore = row[usersAlias[UserTable.spotScore]],
                    createdAt = row[usersAlias[UserTable.createdAt]],
                    updatedAt = row[usersAlias[UserTable.updatedAt]],
                )
            }
    }


    override suspend fun getFriendIdsForUser(userId: UUID): List<UUID> = transaction {
        val friendsAsUser = FriendTable.select(FriendTable.friendId)
            .where { FriendTable.userId eq userId }
            .map { it[FriendTable.friendId] }

        val friendsAsFriend = FriendTable.select(FriendTable.userId)
            .where { FriendTable.friendId eq userId }
            .map { it[FriendTable.userId] }

        (friendsAsUser + friendsAsFriend).distinct()
    }



    override suspend fun getAllFriendsInDb(): List<Friend> {
        return transaction {
            FriendTable
                .selectAll()
                .mapNotNull { row ->
                    Friend(
                        userId = row[FriendTable.userId],
                        friendId = row[FriendTable.friendId],
                        createdAt = row[FriendTable.createdAt]
                    )
                }
        }
    }
}