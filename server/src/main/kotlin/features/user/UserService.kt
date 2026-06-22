package com.carspotter.features.user

import com.carspotter.core.storage.IStorageService
import com.carspotter.features.user.dto.UserDTO
import com.carspotter.features.user.dto.toDTO
import java.util.UUID

interface IUserService {
    suspend fun createUserProfile(authCredentialId: UUID, user: User): UUID
    suspend fun getUserById(userId: UUID): UserDTO?
    suspend fun getUserByAuthCredentialId(authCredentialId: UUID): UserDTO?
    suspend fun updateProfilePicture(userId: UUID, imagePath: String): UserDTO
}

class UserService(
    private val userDao: IUserDAO,
    private val storageService: IStorageService,
) : IUserService {
    companion object {
        private const val minUsernameLength = 3
        private const val maxUsernameLength = 50
        private val usernameRegex = Regex("^[a-z0-9._]+$")
    }

    override suspend fun createUserProfile(authCredentialId: UUID, user: User): UUID {
        require(user.authCredentialId == authCredentialId) { "authCredentialId mismatch" }
        if (userDao.getUserByAuthCredentialId(authCredentialId) != null) {
            throw UserProfileAlreadyExistsException("Profile already exists for this account")
        }

        val normalizedUsername = normalizeUsername(user.username)
        if (userDao.usernameExistsIgnoreCase(normalizedUsername)) {
            throw UsernameAlreadyExistsException("Username is already taken")
        }

        val sanitizedProfilePicture = normalizeOptionalImagePath(user.profilePicturePath)

        return userDao.createUser(
            user.copy(
                username = normalizedUsername,
                profilePicturePath = sanitizedProfilePicture,
            )
        )
    }

    override suspend fun getUserById(userId: UUID): UserDTO? {
        val user = userDao.getUserById(userId) ?: return null
        val postCount = userDao.countPostsByUser(userId)
        return user.toResponse(postCount = postCount.toInt())
    }

    override suspend fun getUserByAuthCredentialId(authCredentialId: UUID): UserDTO? {
        val user = userDao.getUserByAuthCredentialId(authCredentialId) ?: return null
        val postCount = userDao.countPostsByUser(user.id)
        return user.toResponse(postCount = postCount.toInt())
    }

    override suspend fun updateProfilePicture(userId: UUID, imagePath: String): UserDTO {
        val normalizedImagePath = normalizeRequiredImagePath(imagePath)
        val updatedRows = userDao.updateProfilePicture(userId, normalizedImagePath)
        if (updatedRows == 0) {
            throw UserNotFoundException(userId)
        }
        val user = requireNotNull(userDao.getUserById(userId)) { "Updated user could not be loaded" }
        val postCount = userDao.countPostsByUser(userId)
        return user.toResponse(postCount = postCount.toInt())
    }

    private fun normalizeUsername(username: String): String {
        val normalized = username.trim().lowercase()
        require(normalized.isNotBlank()) { "Username cannot be blank" }
        require(normalized.length in minUsernameLength..maxUsernameLength) {
            "Username must be between $minUsernameLength and $maxUsernameLength characters"
        }
        require(usernameRegex.matches(normalized)) {
            "Username may contain only lowercase letters, digits, dot, and underscore"
        }
        return normalized
    }

    private fun normalizeOptionalImagePath(imagePath: String?): String? {
        return imagePath?.trim()?.takeIf { it.isNotEmpty() }?.let(storageService::normalizeObjectKey)
    }

    private fun normalizeRequiredImagePath(imagePath: String): String {
        val normalized = imagePath.trim()
        require(normalized.isNotBlank()) { "Profile picture path cannot be blank" }
        return storageService.normalizeObjectKey(normalized)
    }

    private fun User.toResponse(postCount: Int = 0): UserDTO {
        return toDTO(
            profilePictureUrl = profilePicturePath?.let(storageService::resolveUrl),
            postCount = postCount,
        )
    }
}

class UsernameAlreadyExistsException(message: String) : RuntimeException(message)

class UserProfileAlreadyExistsException(message: String) : RuntimeException(message)

class UserNotFoundException(userId: UUID) : RuntimeException("User $userId not found")
