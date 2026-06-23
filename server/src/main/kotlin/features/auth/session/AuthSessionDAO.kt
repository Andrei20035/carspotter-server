package com.carspotter.features.auth.session

import com.carspotter.features.auth.AuthTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertReturning
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class AuthSessionDAO : IAuthSessionDAO {

    override suspend fun replaceActiveSession(session: NewAuthSession): AuthSession = transaction {
        AuthTable
            .selectAll()
            .where { AuthTable.id eq session.credentialId }
            .forUpdate()
            .single()

        AuthSessionTable.update({
            (AuthSessionTable.credentialId eq session.credentialId) and
                (AuthSessionTable.status eq SessionStatus.ACTIVE.name)
        }) {
            it[status] = SessionStatus.REVOKED.name
            it[revokedReason] = RevokeReason.SUPERSEDED.name
        }

        insertSession(session)
    }

    override suspend fun createSession(session: NewAuthSession): AuthSession = transaction {
        insertSession(session)
    }

    override suspend fun findById(sessionId: UUID): AuthSession? = transaction {
        AuthSessionTable
            .selectAll()
            .where { AuthSessionTable.id eq sessionId }
            .singleOrNull()
            ?.toAuthSession()
    }

    override suspend fun findByRefreshHash(hash: String): AuthSession? = transaction {
        AuthSessionTable
            .selectAll()
            .where { AuthSessionTable.refreshTokenHash eq hash }
            .singleOrNull()
            ?.toAuthSession()
    }

    override suspend fun revokeActiveByCredential(
        credentialId: UUID,
        reason: RevokeReason,
        exceptSessionId: UUID?
    ): Int = transaction {
        val condition = if (exceptSessionId != null) {
            (AuthSessionTable.credentialId eq credentialId) and
                (AuthSessionTable.status eq SessionStatus.ACTIVE.name) and
                (AuthSessionTable.id neq exceptSessionId)
        } else {
            (AuthSessionTable.credentialId eq credentialId) and
                (AuthSessionTable.status eq SessionStatus.ACTIVE.name)
        }
        AuthSessionTable.update({ condition }) {
            it[status] = SessionStatus.REVOKED.name
            it[revokedReason] = reason.name
        }
    }

    override suspend fun rotateRefreshToken(
        sessionId: UUID,
        newHash: String,
        prevHash: String,
        newVersion: Int,
        newIdleExpiresAt: Instant
    ): AuthSession = transaction {
        AuthSessionTable.update({ AuthSessionTable.id eq sessionId }) {
            it[refreshTokenHash] = newHash
            it[prevTokenHash] = prevHash
            it[prevRotatedAt] = Instant.now().atOffset(ZoneOffset.UTC)
            it[version] = newVersion
            it[lastUsedAt] = Instant.now().atOffset(ZoneOffset.UTC)
            it[idleExpiresAt] = newIdleExpiresAt.atOffset(ZoneOffset.UTC)
        }
        AuthSessionTable
            .selectAll()
            .where { AuthSessionTable.id eq sessionId }
            .single()
            .toAuthSession()
    }

    override suspend fun promoteToFull(
        sessionId: UUID,
        userId: UUID,
        newHash: String,
        newVersion: Int
    ): AuthSession = transaction {
        AuthSessionTable.update({ AuthSessionTable.id eq sessionId }) {
            it[AuthSessionTable.userId] = userId
            it[scope] = SessionScope.FULL.name
            it[refreshTokenHash] = newHash
            it[prevTokenHash] = AuthSessionTable.refreshTokenHash
            it[prevRotatedAt] = Instant.now().atOffset(ZoneOffset.UTC)
            it[version] = newVersion
            it[lastUsedAt] = Instant.now().atOffset(ZoneOffset.UTC)
        }
        AuthSessionTable
            .selectAll()
            .where { AuthSessionTable.id eq sessionId }
            .single()
            .toAuthSession()
    }

    override suspend fun promoteToFullAtomically(
        sessionId: UUID,
        userId: UUID,
        newHash: String,
        now: Instant,
    ): AuthSession? = transaction {
        val current = AuthSessionTable
            .selectAll()
            .where { AuthSessionTable.id eq sessionId }
            .forUpdate()
            .singleOrNull()
            ?.toAuthSession()
            ?: return@transaction null

        AuthSessionTable.update({ AuthSessionTable.id eq sessionId }) {
            it[AuthSessionTable.userId] = userId
            it[scope] = SessionScope.FULL.name
            it[refreshTokenHash] = newHash
            it[prevTokenHash] = current.refreshTokenHash
            it[prevRotatedAt] = now.atOffset(ZoneOffset.UTC)
            it[version] = current.version + 1
            it[lastUsedAt] = now.atOffset(ZoneOffset.UTC)
        }

        AuthSessionTable
            .selectAll()
            .where { AuthSessionTable.id eq sessionId }
            .single()
            .toAuthSession()
    }

    override suspend fun rotateForPasswordChangeAtomically(
        sessionId: UUID,
        newHash: String,
        now: Instant,
        newIdleExpiresAt: Instant,
    ): AuthSession? = transaction {
        val current = AuthSessionTable
            .selectAll()
            .where { AuthSessionTable.id eq sessionId }
            .forUpdate()
            .singleOrNull()
            ?.toAuthSession()
            ?: return@transaction null

        AuthTable
            .selectAll()
            .where { AuthTable.id eq current.credentialId }
            .forUpdate()
            .single()

        AuthSessionTable.update({
            (AuthSessionTable.credentialId eq current.credentialId) and
                (AuthSessionTable.status eq SessionStatus.ACTIVE.name) and
                (AuthSessionTable.id neq sessionId)
        }) {
            it[status] = SessionStatus.REVOKED.name
            it[revokedReason] = RevokeReason.PASSWORD_CHANGED.name
        }

        AuthSessionTable.update({ AuthSessionTable.id eq sessionId }) {
            it[refreshTokenHash] = newHash
            it[prevTokenHash] = current.refreshTokenHash
            it[prevRotatedAt] = now.atOffset(ZoneOffset.UTC)
            it[version] = current.version + 1
            it[lastUsedAt] = now.atOffset(ZoneOffset.UTC)
            it[idleExpiresAt] = newIdleExpiresAt.atOffset(ZoneOffset.UTC)
        }

        AuthSessionTable
            .selectAll()
            .where { AuthSessionTable.id eq sessionId }
            .single()
            .toAuthSession()
    }

    override suspend fun revokeSession(sessionId: UUID, reason: RevokeReason): Int = transaction {
        AuthSessionTable.update({ AuthSessionTable.id eq sessionId }) {
            it[status] = SessionStatus.REVOKED.name
            it[revokedReason] = reason.name
        }
    }

    override suspend fun listActiveSessions(credentialId: UUID): List<AuthSession> = transaction {
        AuthSessionTable
            .selectAll()
            .where {
                (AuthSessionTable.credentialId eq credentialId) and
                    (AuthSessionTable.status eq SessionStatus.ACTIVE.name)
            }
            .map { it.toAuthSession() }
    }

    override suspend fun rotateRefreshTokenAtomically(
        presentedHash: String,
        newHash: String,
        now: Instant,
        newIdleExpiresAt: Instant,
        graceWindowSeconds: Long,
    ): RefreshRotationResult = transaction {
        val row = AuthSessionTable
            .selectAll()
            .where {
                (AuthSessionTable.refreshTokenHash eq presentedHash) or
                    (AuthSessionTable.prevTokenHash eq presentedHash)
            }
            .forUpdate()
            .singleOrNull()
            ?: return@transaction RefreshRotationResult.Invalid

        val session = row.toAuthSession()
        if (session.status != SessionStatus.ACTIVE) {
            return@transaction RefreshRotationResult.Revoked
        }

        if (now > session.idleExpiresAt || now > session.absoluteExpiresAt) {
            val reason = if (now > session.absoluteExpiresAt) {
                RevokeReason.ABSOLUTE_EXPIRED
            } else {
                RevokeReason.IDLE_EXPIRED
            }
            AuthSessionTable.update({ AuthSessionTable.id eq session.id }) {
                it[status] = SessionStatus.REVOKED.name
                it[revokedReason] = reason.name
            }
            return@transaction RefreshRotationResult.Expired
        }

        if (session.refreshTokenHash == presentedHash) {
            AuthSessionTable.update({ AuthSessionTable.id eq session.id }) {
                it[refreshTokenHash] = newHash
                it[prevTokenHash] = presentedHash
                it[prevRotatedAt] = now.atOffset(ZoneOffset.UTC)
                it[version] = session.version + 1
                it[lastUsedAt] = now.atOffset(ZoneOffset.UTC)
                it[idleExpiresAt] = newIdleExpiresAt.atOffset(ZoneOffset.UTC)
            }
            val rotated = AuthSessionTable
                .selectAll()
                .where { AuthSessionTable.id eq session.id }
                .single()
                .toAuthSession()
            return@transaction RefreshRotationResult.Rotated(rotated)
        }

        val rotatedAt = session.prevRotatedAt ?: Instant.MIN
        if (now < rotatedAt.plusSeconds(graceWindowSeconds)) {
            RefreshRotationResult.Consumed
        } else {
            AuthSessionTable.update({ AuthSessionTable.id eq session.id }) {
                it[status] = SessionStatus.REVOKED.name
                it[revokedReason] = RevokeReason.REFRESH_TOKEN_REUSED.name
            }
            RefreshRotationResult.Reused
        }
    }

    private fun insertSession(session: NewAuthSession): AuthSession {
        val row = AuthSessionTable.insertReturning {
            it[credentialId] = session.credentialId
            it[userId] = session.userId
            it[scope] = session.scope.name
            it[refreshTokenHash] = session.refreshTokenHash
            it[deviceId] = session.deviceId
            it[deviceName] = session.deviceName
            it[userAgent] = session.userAgent
            it[ipAddress] = session.ipAddress
            it[idleExpiresAt] = session.idleExpiresAt.atOffset(ZoneOffset.UTC)
            it[absoluteExpiresAt] = session.absoluteExpiresAt.atOffset(ZoneOffset.UTC)
        }.single()
        return row.toAuthSession()
    }

    private fun org.jetbrains.exposed.sql.ResultRow.toAuthSession(): AuthSession = AuthSession(
        id = this[AuthSessionTable.id].value,
        credentialId = this[AuthSessionTable.credentialId].value,
        userId = this[AuthSessionTable.userId]?.value,
        version = this[AuthSessionTable.version],
        scope = SessionScope.valueOf(this[AuthSessionTable.scope]),
        refreshTokenHash = this[AuthSessionTable.refreshTokenHash],
        prevTokenHash = this[AuthSessionTable.prevTokenHash],
        prevRotatedAt = this[AuthSessionTable.prevRotatedAt]?.toInstant(),
        status = SessionStatus.valueOf(this[AuthSessionTable.status]),
        revokedReason = this[AuthSessionTable.revokedReason]?.let { RevokeReason.valueOf(it) },
        deviceId = this[AuthSessionTable.deviceId],
        deviceName = this[AuthSessionTable.deviceName],
        userAgent = this[AuthSessionTable.userAgent],
        ipAddress = this[AuthSessionTable.ipAddress],
        createdAt = this[AuthSessionTable.createdAt].toInstant(),
        lastUsedAt = this[AuthSessionTable.lastUsedAt].toInstant(),
        idleExpiresAt = this[AuthSessionTable.idleExpiresAt].toInstant(),
        absoluteExpiresAt = this[AuthSessionTable.absoluteExpiresAt].toInstant(),
    )
}
