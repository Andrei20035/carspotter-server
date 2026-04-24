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

    /**
     * The DB enforces `user_id_1 < user_id_2` (chk_friend_pair_order) and the
     * primary key is (user_id_1, user_id_2), so a friendship is stored as a
     * single canonical row. Always sort the two UUIDs before touching the table.
     */
    private fun canonicalPair(a: UUID, b: UUID): Pair<UUID, UUID> =
        if (a < b) a to b else b to a

    override suspend fun addFriend(userId: UUID, friendId: UUID): UUID {
        require(userId != friendId) { "Cannot add yourself as a friend" }
        val (u1, u2) = canonicalPair(userId, friendId)

        return transaction {
            FriendTable.insertReturning(listOf(FriendTable.userId1, FriendTable.userId2)) {
                it[FriendTable.userId1] = u1
                it[FriendTable.userId2] = u2
            }.singleOrNull() ?: throw IllegalStateException("Failed to insert friendship: $userId <-> $friendId")

            // Preserve the old return contract: echo back the friendId the caller asked for.
            friendId
        }
    }

    override suspend fun deleteFriend(userId: UUID, friendId: UUID): Int {
        val (u1, u2) = canonicalPair(userId, friendId)
        return transaction {
            FriendTable.deleteWhere {
                (FriendTable.userId1 eq u1) and (FriendTable.userId2 eq u2)
            }
        }
    }

    override suspend fun getAllFriends(userId: UUID): List<User> = transaction {
        val usersAlias = UserTable.alias("u")

        // The "other side" of a friendship row is:
        //   - userId2 when userId1 = userId
        //   - userId1 when userId2 = userId
        FriendTable
            .join(
                usersAlias,
                JoinType.INNER,
                additionalConstraint = {
                    ((FriendTable.userId1 eq userId) and (FriendTable.userId2 eq usersAlias[UserTable.id])) or
                            ((FriendTable.userId2 eq userId) and (FriendTable.userId1 eq usersAlias[UserTable.id]))
                }
            )
            .selectAll()
            .where { (FriendTable.userId1 eq userId) or (FriendTable.userId2 eq userId) }
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
        val asUser1 = FriendTable.select(FriendTable.userId2)
            .where { FriendTable.userId1 eq userId }
            .map { it[FriendTable.userId2] }

        val asUser2 = FriendTable.select(FriendTable.userId1)
            .where { FriendTable.userId2 eq userId }
            .map { it[FriendTable.userId1] }

        (asUser1 + asUser2).distinct()
    }

    override suspend fun getAllFriendsInDb(): List<Friend> {
        return transaction {
            FriendTable
                .selectAll()
                .map { row ->
                    Friend(
                        userId = row[FriendTable.userId1],
                        friendId = row[FriendTable.userId2],
                        createdAt = row[FriendTable.createdAt]
                    )
                }
        }
    }
}