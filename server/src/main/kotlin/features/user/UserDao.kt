package com.carspotter.features.user

import com.carspotter.features.auth.AuthTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

interface IUserDAO {
    suspend fun createUser(user: User): UUID
    suspend fun getUserById(userId: UUID): User?
    suspend fun getUserByAuthCredentialId(authCredentialId: UUID): User?
    suspend fun usernameExistsIgnoreCase(username: String): Boolean
    suspend fun updateProfilePicture(userId: UUID, imagePath: String): Int
}

class UserDao : IUserDAO {
    override suspend fun createUser(user: User): UUID = transaction {
        UserTable.insertReturning(listOf(UserTable.id)) {
            it[authCredentialId] = user.authCredentialId
            it[profilePicturePath] = user.profilePicturePath
            it[fullName] = user.fullName
            it[phoneNumber] = user.phoneNumber
            it[birthDate] = user.birthDate
            it[username] = user.username
            it[country] = user.country
            it[spotScore] = user.spotScore
        }.singleOrNull()?.get(UserTable.id)?.value ?: throw UserCreationException("Failed to insert user")
    }

    override suspend fun getUserById(userId: UUID): User? = transaction {
        UserTable
            .selectAll()
            .where { UserTable.id eq userId }
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
            .singleOrNull()
    }

    override suspend fun getUserByAuthCredentialId(authCredentialId: UUID): User? = transaction {
        UserTable
            .selectAll()
            .where { UserTable.authCredentialId eq authCredentialId }
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
            .singleOrNull()
    }

    override suspend fun usernameExistsIgnoreCase(username: String): Boolean = transaction {
        UserTable
            .select(UserTable.id)
            .where { UserTable.username.lowerCase() eq username.lowercase() }
            .limit(1)
            .any()
    }

    override suspend fun updateProfilePicture(userId: UUID, imagePath: String): Int = transaction {
        UserTable.update({ UserTable.id eq userId }) {
            it[profilePicturePath] = imagePath
        }
    }
}

class UserCreationException(message: String) : Exception(message)
