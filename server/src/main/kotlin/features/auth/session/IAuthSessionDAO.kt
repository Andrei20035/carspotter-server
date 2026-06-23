package com.carspotter.features.auth.session

import java.time.Instant
import java.util.UUID

interface IAuthSessionDAO {
    suspend fun replaceActiveSession(session: NewAuthSession): AuthSession
    suspend fun createSession(session: NewAuthSession): AuthSession
    suspend fun findById(sessionId: UUID): AuthSession?
    suspend fun findByRefreshHash(hash: String): AuthSession?
    suspend fun revokeActiveByCredential(
        credentialId: UUID,
        reason: RevokeReason,
        exceptSessionId: UUID? = null
    ): Int
    suspend fun rotateRefreshToken(
        sessionId: UUID,
        newHash: String,
        prevHash: String,
        newVersion: Int,
        newIdleExpiresAt: Instant
    ): AuthSession
    suspend fun promoteToFull(
        sessionId: UUID,
        userId: UUID,
        newHash: String,
        newVersion: Int
    ): AuthSession
    suspend fun promoteToFullAtomically(
        sessionId: UUID,
        userId: UUID,
        newHash: String,
        now: Instant,
    ): AuthSession?
    suspend fun rotateForPasswordChangeAtomically(
        sessionId: UUID,
        newHash: String,
        now: Instant,
        newIdleExpiresAt: Instant,
    ): AuthSession?
    suspend fun revokeSession(sessionId: UUID, reason: RevokeReason): Int
    suspend fun listActiveSessions(credentialId: UUID): List<AuthSession>
    suspend fun rotateRefreshTokenAtomically(
        presentedHash: String,
        newHash: String,
        now: Instant,
        newIdleExpiresAt: Instant,
        graceWindowSeconds: Long,
    ): RefreshRotationResult
}
