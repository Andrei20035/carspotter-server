package com.carspotter.features.user

import com.carspotter.features.auth.AuthTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

interface IUserDAO {
    suspend fun createUser(user: User): UUID
    suspend fun getUserByID(userId: UUID): User?
    suspend fun getUserByUsername(username: String): List<User>
    suspend fun getAllUsers(): List<User>
    suspend fun updateProfilePicture(userId: UUID, imagePath: String): Int
    suspend fun deleteUser(credentialId: UUID): Int
}

class UserDao : IUserDAO {
    override suspend fun createUser(user: User): UUID {
        return transaction {
            UserTable.insertReturning(listOf(UserTable.id)) {
                it[authCredentialId] = user.authCredentialId
                it[profilePicturePath] = user.profilePicturePath
                it[fullName] = user.fullName
                it[phoneNumber] =  user.phoneNumber
                it[birthDate] = user.birthDate
                it[username] = user.username
                it[country] = user.country
                it[spotScore] = user.spotScore
            }.singleOrNull()?.get(UserTable.id)?.value ?: throw UserCreationException("Failed to insert user")
        }
    }

    override suspend fun getUserByID(userId: UUID): User? {
        return transaction {
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
                }.singleOrNull()
        }
    }

    override suspend fun getUserByUsername(username: String): List<User> {
        return transaction {
            UserTable
                .selectAll()
                .where { UserTable.username.lowerCase() like "${username.lowercase()}%" }
                .map { row ->
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

    override suspend fun getAllUsers(): List<User> {
        return transaction {
            UserTable
                .selectAll()
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

    override suspend fun updateProfilePicture(userId: UUID, imagePath: String): Int {
        return transaction {
            UserTable
                .update({ UserTable.id eq userId }) {
                    it[profilePicturePath] = imagePath
                }
        }
    }

    override suspend fun deleteUser(credentialId: UUID): Int {
        return transaction {
            AuthTable.deleteWhere { id eq credentialId }
        }
    }

}

class UserCreationException(message: String) : Exception(message)