package com.carspotter.features.user

import java.util.*

interface IUserRepository {
    suspend fun createUser(user: User): UUID
    suspend fun getUserByID(userId: UUID): User?
    suspend fun getUserByUsername(username: String): List<User>
    suspend fun getAllUsers(): List<User>
    suspend fun updateProfilePicture(userId: UUID, imagePath: String): Int
    suspend fun deleteUser(credentialId: UUID): Int
}

class UserRepository(
    private val userDao: IUserDAO
) : IUserRepository {
    override suspend fun createUser(user: User): UUID {
        return userDao.createUser(user)
    }

    override suspend fun getUserByID(userId: UUID): User? {
        return userDao.getUserByID(userId)
    }

    override suspend fun getUserByUsername(username: String): List<User> {
        return userDao.getUserByUsername(username)
    }

    override suspend fun getAllUsers(): List<User> {
        return userDao.getAllUsers()
    }

    override suspend fun updateProfilePicture(userId: UUID, imagePath: String): Int {
        return userDao.updateProfilePicture(userId, imagePath)
    }

    override suspend fun deleteUser(credentialId: UUID): Int {
        return userDao.deleteUser(credentialId)
    }
}