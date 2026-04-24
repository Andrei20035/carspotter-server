package com.carspotter.features.friend_request

import com.carspotter.features.user.User
import com.carspotter.features.friend.FriendTable
import com.carspotter.features.user.UserTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

interface IFriendRequestDAO {
    suspend fun sendFriendRequest(senderId: UUID, receiverId: UUID): UUID
    suspend fun acceptFriendRequest(senderId: UUID, receiverId: UUID): Boolean
    suspend fun declineFriendRequest(senderId: UUID, receiverId: UUID): Int
    suspend fun getAllFriendRequests(userId: UUID): List<User>
    suspend fun getAllFriendReqFromDB(): List<FriendRequest>
}

class FriendRequestDAO : IFriendRequestDAO {
    override suspend fun sendFriendRequest(senderId: UUID, receiverId: UUID): UUID {
        return transaction {
            FriendRequestTable
                .insertReturning(listOf(FriendRequestTable.senderId, FriendRequestTable.receiverId)) {
                    it[this.senderId] = senderId
                    it[this.receiverId] = receiverId
                }.singleOrNull()?.get(FriendRequestTable.senderId)
                ?: throw IllegalStateException("Failed to add friend request to database")
        }
    }

    override suspend fun acceptFriendRequest(senderId: UUID, receiverId: UUID): Boolean {
        // The friends table stores a single symmetric row and enforces
        // user_id_1 < user_id_2 (chk_friend_pair_order). Sort the pair before inserting.
        val (u1, u2) = if (senderId < receiverId) senderId to receiverId else receiverId to senderId

        return transaction {
            val deletedRows = FriendRequestTable.deleteWhere {
                (FriendRequestTable.senderId eq senderId) and (FriendRequestTable.receiverId eq receiverId)
            }

            val insert = FriendTable.insert {
                it[userId1] = u1
                it[userId2] = u2
            }

            deletedRows == 1 && insert.insertedCount == 1
        }
    }

    override suspend fun declineFriendRequest(senderId: UUID, receiverId: UUID): Int {
        return transaction {
            FriendRequestTable.deleteWhere {
                (FriendRequestTable.senderId eq senderId) and (FriendRequestTable.receiverId eq receiverId)
            }
        }
    }

    override suspend fun getAllFriendRequests(userId: UUID): List<User> {
        return transaction {
            // Query for friends where `userId` is the initiator
            val friendsAsInitiator = UserTable.alias("u1").let { usersAlias ->
                FriendRequestTable
                    .join(
                        usersAlias,
                        JoinType.INNER,
                        additionalConstraint = { FriendRequestTable.senderId eq usersAlias[UserTable.id] })
                    .selectAll()
                    .where { FriendRequestTable.receiverId eq userId }
                    .map { row ->
                        User(
                            id = row[usersAlias[UserTable.id]].value,
                            authCredentialId = row[usersAlias[UserTable.authCredentialId]],
                            profilePicturePath = row[usersAlias[UserTable.profilePicturePath]],
                            fullName = row[usersAlias[UserTable.fullName]],
                            phoneNumber =  row[usersAlias[UserTable.phoneNumber]],
                            birthDate = row[usersAlias[UserTable.birthDate]],
                            username = row[usersAlias[UserTable.username]],
                            country = row[usersAlias[UserTable.country]],
                            spotScore = row[usersAlias[UserTable.spotScore]],
                            createdAt = row[usersAlias[UserTable.createdAt]],
                            updatedAt = row[usersAlias[UserTable.updatedAt]],
                        )
                    }
            }

            // Query for friends where `userId` is the recipient
            val friendsAsRecipient = UserTable.alias("u2").let { usersAlias ->
                FriendRequestTable
                    .join(
                        usersAlias,
                        JoinType.INNER,
                        additionalConstraint = { FriendRequestTable.receiverId eq usersAlias[UserTable.id] })
                    .selectAll()
                    .where { FriendRequestTable.senderId eq userId }
                    .map { row ->
                        User(
                            id = row[usersAlias[UserTable.id]].value,
                            authCredentialId = row[usersAlias[UserTable.authCredentialId]],
                            fullName = row[usersAlias[UserTable.fullName]],
                            phoneNumber = row[usersAlias[UserTable.phoneNumber]],
                            profilePicturePath = row[usersAlias[UserTable.profilePicturePath]],
                            birthDate = row[usersAlias[UserTable.birthDate]],
                            username = row[usersAlias[UserTable.username]],
                            country = row[usersAlias[UserTable.country]],
                            spotScore = row[usersAlias[UserTable.spotScore]],
                            createdAt = row[usersAlias[UserTable.createdAt]],
                            updatedAt = row[usersAlias[UserTable.updatedAt]],
                        )
                    }
            }

            // Combine results and remove duplicates
            (friendsAsInitiator + friendsAsRecipient).distinctBy { it.id }
        }
    }

    override suspend fun getAllFriendReqFromDB(): List<FriendRequest> {
        return transaction {
            FriendRequestTable
                .selectAll()
                .mapNotNull { row ->
                    FriendRequest(
                        senderId = row[FriendRequestTable.senderId],
                        receiverId = row[FriendRequestTable.receiverId],
                        createdAt = row[FriendRequestTable.createdAt]
                    )
                }
        }
    }

}