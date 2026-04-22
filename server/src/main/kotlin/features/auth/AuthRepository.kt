package com.carspotter.features.auth

import java.util.*

interface IAuthRepository {
    suspend fun createCredentials(authCredential: AuthCredential): UUID
    suspend fun getCredentialsForLogin(email: String): AuthCredential?
    suspend fun getCredentialsById(credentialId: UUID): AuthCredential?
    suspend fun updatePassword(credentialId: UUID, newPassword: String): Int
    suspend fun deleteCredentials(credentialId: UUID): Int
    suspend fun getAllCredentials(): List<AuthCredential>
}

class AuthRepository(
    private val authCredentialDao: IAuthDAO
): IAuthRepository {
    override suspend fun createCredentials(authCredential: AuthCredential): UUID {
        return authCredentialDao.createCredentials(authCredential)
    }

    override suspend fun getCredentialsForLogin(email: String): AuthCredential? {
        return authCredentialDao.getCredentialsForLogin(email)
    }

    override suspend fun getCredentialsById(credentialId: UUID): AuthCredential? {
        return authCredentialDao.getCredentialsById(credentialId)
    }

    override suspend fun updatePassword(credentialId: UUID, newPassword: String): Int {
        return authCredentialDao.updatePassword(credentialId, newPassword)
    }

    override suspend fun deleteCredentials(credentialId: UUID): Int {
        return authCredentialDao.deleteCredentials(credentialId)
    }

    override suspend fun getAllCredentials(): List<AuthCredential> {
        return authCredentialDao.getAllCredentials()
    }
}