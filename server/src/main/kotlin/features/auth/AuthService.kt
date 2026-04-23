package com.carspotter.features.auth

import at.favre.lib.crypto.bcrypt.BCrypt
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.carspotter.features.auth.dto.AuthDTO
import com.carspotter.features.auth.dto.toDTO
import com.carspotter.features.comment.dto.toDTO
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import java.util.*

data class GoogleUser(
    val email: String,
    val googleId: String
)

interface GoogleTokenVerifier {
    fun verify(googleIdToken: String): GoogleUser?
}

class GoogleTokenVerifierImpl : GoogleTokenVerifier {

    private val clientId: String = System.getenv("GOOGLE_CLIENT_ID")
        ?: throw IllegalStateException("GOOGLE_CLIENT_ID is not set")

    private val verifier = GoogleIdTokenVerifier.Builder(
        NetHttpTransport(),
        GsonFactory.getDefaultInstance()
    )
        .setAudience(listOf(clientId))
        .build()

    override fun verify(googleIdToken: String): GoogleUser? {
        val idToken = verifier.verify(googleIdToken) ?: return null
        val payload = idToken.payload

        val email = payload.email ?: return null
        val emailVerified = payload.emailVerified

        if (emailVerified != true) return null

        return GoogleUser(
            email = email.trim().lowercase(),
            googleId = payload.subject
        )
    }
}

interface IAuthService {
    suspend fun createCredentials(authCredential: AuthCredential): UUID
    suspend fun regularLogin(email: String, password: String): AuthDTO?
    suspend fun googleLogin(googleIdToken: String): AuthDTO?
    suspend fun updatePassword(credentialId: UUID, oldPassword: String, newPassword: String): Int
    suspend fun deleteCredentials(credentialId: UUID): Int
    suspend fun getCredentialsById(credentialId: UUID): AuthCredential?
}

class AuthService(
    private val authDao: IAuthDAO,
    private val googleTokenVerifier: GoogleTokenVerifier = GoogleTokenVerifierImpl()
) : IAuthService {

    override suspend fun createCredentials(authCredential: AuthCredential): UUID {
        val normalizedEmail = authCredential.email.trim().lowercase()

        val existing = authDao.getCredentialsForLogin(normalizedEmail)
        if (existing != null) {
            throw IllegalArgumentException("Email is already registered")
        }

        val credentialToSave = when (authCredential.provider) {
            AuthProvider.REGULAR -> {
                val password = authCredential.password
                    ?: throw IllegalArgumentException("Password is required")

                val hashedPassword = BCrypt.withDefaults()
                    .hashToString(12, password.toCharArray())

                authCredential.copy(
                    email = normalizedEmail,
                    password = hashedPassword,
                    googleId = null
                )
            }

            AuthProvider.GOOGLE -> {
                val googleId = authCredential.googleId
                    ?: throw IllegalArgumentException("Google ID is required")

                authCredential.copy(
                    email = normalizedEmail,
                    password = null,
                    googleId = googleId
                )
            }
        }

        return try {
            authDao.createCredentials(credentialToSave)
        } catch (e: IllegalStateException) {
            throw CredentialCreationException("Unable to create credentials", e)
        }
    }

    override suspend fun regularLogin(email: String, password: String): AuthDTO? {
        val normalizedEmail = email.trim().lowercase()

        val authCredential = authDao.getCredentialsForLogin(normalizedEmail)
            ?: return null

        if (authCredential.provider != AuthProvider.REGULAR) {
            return null
        }

        val storedPassword = authCredential.password ?: return null

        val passwordValid = BCrypt.verifyer()
            .verify(password.toCharArray(), storedPassword)
            .verified

        return if (passwordValid) {
            authCredential.toDTO()
        } else {
            null
        }
    }

    override suspend fun googleLogin(googleIdToken: String): AuthDTO? {
        val googleUser = googleTokenVerifier.verify(googleIdToken)
            ?: return null

        val existingCredential = authDao.getCredentialsForLogin(googleUser.email)

        return when {
            existingCredential != null &&
                    existingCredential.provider == AuthProvider.GOOGLE &&
                    existingCredential.googleId == googleUser.googleId -> {
                existingCredential.toDTO()
            }

            existingCredential != null &&
                    existingCredential.provider != AuthProvider.GOOGLE -> {
                null
            }

            existingCredential == null -> {
                val newCredential = AuthCredential(
                    email = googleUser.email,
                    password = null,
                    provider = AuthProvider.GOOGLE,
                    googleId = googleUser.googleId
                )

                val credentialId = createCredentials(newCredential)

                newCredential.copy(id = credentialId).toDTO()
            }

            else -> null
        }
    }

    override suspend fun updatePassword(
        credentialId: UUID,
        oldPassword: String,
        newPassword: String
    ): Int {
        val authCredential = authDao.getCredentialsById(credentialId)
            ?: throw IllegalArgumentException("Credentials not found")

        if (authCredential.provider != AuthProvider.REGULAR) {
            throw IllegalArgumentException("Password cannot be updated for this provider")
        }

        val storedPassword = authCredential.password
            ?: throw IllegalArgumentException("Password not found")

        val oldPasswordValid = BCrypt.verifyer()
            .verify(oldPassword.toCharArray(), storedPassword)
            .verified

        if (!oldPasswordValid) {
            throw IllegalArgumentException("Invalid current password")
        }

        val newHashedPassword = BCrypt.withDefaults()
            .hashToString(12, newPassword.toCharArray())

        return authDao.updatePassword(credentialId, newHashedPassword)
    }

    override suspend fun deleteCredentials(credentialId: UUID): Int {
        return authDao.deleteCredentials(credentialId)
    }

    override suspend fun getCredentialsById(credentialId: UUID): AuthCredential? {
        return authDao.getCredentialsById(credentialId)
    }
}

class CredentialCreationException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

