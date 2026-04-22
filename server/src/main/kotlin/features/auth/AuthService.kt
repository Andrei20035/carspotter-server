package com.carspotter.features.auth

import at.favre.lib.crypto.bcrypt.BCrypt
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.carspotter.features.auth.dto.AuthDTO
import com.carspotter.features.comment.dto.toDTO
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import java.util.*

interface GoogleTokenVerifier {
    fun verifyAndExtractSub(googleIdToken: String): String?
}

class GoogleTokenVerifierImpl : GoogleTokenVerifier {
    override fun verifyAndExtractSub(googleIdToken: String): String? {
        val clientId = System.getenv("GOOGLE_CLIENT_ID")
        val transport = NetHttpTransport()
        val jsonFactory = GsonFactory.getDefaultInstance()
        val verifier = GoogleIdTokenVerifier.Builder(transport, jsonFactory)
            .setAudience(listOf(System.getenv("GOOGLE_CLIENT_ID")))
            .build()
        val idToken: GoogleIdToken? = verifier.verify(googleIdToken)
        return idToken?.payload?.subject
    }
}

interface IAuthService {
    suspend fun createCredentials(authCredential: AuthCredential): UUID
    suspend fun regularLogin(email: String, password: String): AuthDTO?
    suspend fun googleLogin(email: String, googleIdToken: String): AuthDTO?
    suspend fun updatePassword(credentialId: UUID, newPassword: String): Int
    suspend fun deleteCredentials(credentialId: UUID): Int
    suspend fun getCredentialsById(credentialId: UUID): AuthCredential?
}

class AuthService(
    private val authCredentialRepository: IAuthRepository,
    private val googleTokenVerifier: GoogleTokenVerifier = GoogleTokenVerifierImpl()
) : IAuthService {
    override suspend fun createCredentials(authCredential: AuthCredential): UUID {

        val existing = authCredentialRepository.getCredentialsForLogin(authCredential.email)

        if(existing != null) {
            throw IllegalArgumentException("Email is already registered")
        }

        val hashedPassword = when (authCredential.provider) {
            AuthProvider.REGULAR -> BCrypt.withDefaults().hashToString(12, authCredential.password?.toCharArray())
            AuthProvider.GOOGLE -> null
        }

        val authCredentialsToSave = authCredential.copy(password = hashedPassword)

        return try {
            authCredentialRepository.createCredentials(authCredentialsToSave)
        } catch (e: IllegalStateException) {
            throw CredentialCreationException("Unable to create credentials", e)
        }
    }

    override suspend fun regularLogin(email: String, password: String): AuthDTO? {
        val authCredential = authCredentialRepository.getCredentialsForLogin(email) ?: return null

        if (authCredential.provider == AuthProvider.REGULAR && BCrypt.verifyer()
                .verify(password.toCharArray(), authCredential.password).verified
        ) {
            return authCredential.toDTO()
        }
        return null
    }

    override suspend fun googleLogin(email: String, googleIdToken: String): AuthDTO? {
        val authCredential = authCredentialRepository.getCredentialsForLogin(email)
        val googleSub = googleTokenVerifier.verifyAndExtractSub(googleIdToken) ?: return null

        return when {
            authCredential != null &&
                    authCredential.provider == AuthProvider.GOOGLE &&
                    authCredential.googleId == googleSub -> {
                authCredential.toDTO()
            }

            authCredential != null &&
                    authCredential.provider != AuthProvider.GOOGLE -> {
                null
            }

            else -> {
                val newCredential = AuthCredential(
                    email = email,
                    password = null,
                    provider = AuthProvider.GOOGLE,
                    googleId = googleSub
                )
                val credentialId = authCredentialRepository.createCredentials(newCredential)
                newCredential.copy(id = credentialId).toDTO()
            }
        }
    }

    override suspend fun updatePassword(credentialId: UUID, newPassword: String): Int {
        val newHashedPassword = BCrypt.withDefaults().hashToString(12, newPassword.toCharArray())
        return authCredentialRepository.updatePassword(credentialId, newHashedPassword)
    }

    override suspend fun deleteCredentials(credentialId: UUID): Int {
        return authCredentialRepository.deleteCredentials(credentialId)
    }

    override suspend fun getCredentialsById(credentialId: UUID): AuthCredential? {
        return authCredentialRepository.getCredentialsById(credentialId)
    }
}

class CredentialCreationException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class JwtService(
    private val jwtSecret: String,
    private val jwtIssuer: String,
    private val jwtAudience: String
) {
    fun generateJwtToken(credentialId: UUID, userId: UUID? = null, email: String, isAdmin: Boolean = false): Map<String, String> {
        val tokenBuilder = JWT.create()
            .withAudience(jwtAudience)
            .withIssuer(jwtIssuer)
            .withClaim("credentialId", credentialId.toString())
            .withClaim("email", email)
            .withClaim("isAdmin", isAdmin)
            .withExpiresAt(Date(System.currentTimeMillis() + 86400000))

        if (userId != null) {
            tokenBuilder.withClaim("userId", userId.toString())
        }

        val token = tokenBuilder.sign(Algorithm.HMAC256(jwtSecret))

        return mapOf("token" to token)
    }
}