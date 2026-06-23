package com.carspotter.features.auth.session

import java.util.UUID

interface ISessionService {
    suspend fun createSession(
        credentialId: UUID,
        scope: SessionScope,
        userId: UUID? = null,
        deviceId: String?,
        deviceName: String?,
        userAgent: String?,
        ip: String?,
    ): Pair<AuthSession, String>

    suspend fun refreshTokens(
        rawRefreshToken: String,
        deviceId: String? = null,
    ): Pair<AuthSession, String>

    suspend fun revokeSession(sessionId: UUID, reason: RevokeReason)

    suspend fun revokeAllSessions(
        credentialId: UUID,
        reason: RevokeReason,
        exceptSessionId: UUID? = null,
    )

    suspend fun promoteSession(sessionId: UUID, userId: UUID): Pair<AuthSession, String>

    suspend fun rotateForPasswordChange(sessionId: UUID): Pair<AuthSession, String>

    suspend fun validateSessionForRequest(
        sessionId: UUID,
        credentialId: UUID,
        version: Int,
    ): AuthSession?
}
