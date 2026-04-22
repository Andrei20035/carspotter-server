package com.carspotter.features.like

import com.carspotter.features.user.User
import com.carspotter.features.user.UserTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertReturning
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

interface ILikeDAO {
    suspend fun likePost(userId: UUID, postId: UUID): UUID
    suspend fun unlikePost(userId: UUID, postId: UUID): Int
    suspend fun getLikesForPost(postId: UUID): List<User>
    suspend fun hasUserLikedPost(userId: UUID, postId: UUID): Boolean
}

class LikeDAO : ILikeDAO {
    override suspend fun likePost(userId: UUID, postId: UUID): UUID {
        return transaction {
            LikeTable
                .insertReturning(listOf(LikeTable.id)) {
                    it[LikeTable.userId] = userId
                    it[LikeTable.postId] = postId
                }.singleOrNull()?.get(LikeTable.id)?.value
                ?: throw IllegalStateException("Failed to insert like for user $userId and post $postId")
        }
    }

    override suspend fun unlikePost(userId: UUID, postId: UUID): Int {
        return transaction {
            LikeTable.deleteWhere {
                (LikeTable.userId eq userId) and (LikeTable.postId eq postId)
            }
        }
    }

    override suspend fun getLikesForPost(postId: UUID): List<User> {
        return transaction {
            (LikeTable innerJoin UserTable)
                .selectAll()
                .where { LikeTable.postId eq postId }
                .mapNotNull { row ->
                    User(
                        id = row[UserTable.id].value,
                        authCredentialId = row[UserTable.authCredentialId],
                        profilePicturePath = row[UserTable.profilePicturePath],
                        fullName = row[UserTable.fullName],
                        phoneNumber = row[UserTable.phoneNumber],
                        birthDate = row[UserTable.birthDate],
                        username = row[UserTable.username],
                        country = row[UserTable.country],
                        spotScore = row[UserTable.spotScore],
                        createdAt = row[UserTable.createdAt],
                        updatedAt = row[UserTable.updatedAt]
                    )
                }
        }
    }

    override suspend fun hasUserLikedPost(userId: UUID, postId: UUID): Boolean {
        return transaction {
            LikeTable
                .selectAll()
                .where { (LikeTable.userId eq userId) and (LikeTable.postId eq postId) }
                .any()
        }
    }
}