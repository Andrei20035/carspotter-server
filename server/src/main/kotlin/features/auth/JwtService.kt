package com.carspotter.features.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.TokenExpiredException
import com.auth0.jwt.exceptions.JWTVerificationException
import com.carspotter.features.auth.session.AuthSession
import com.carspotter.features.auth.session.ISessionService
import com.carspotter.features.auth.session.SessionScope
import com.carspotter.features.auth.session.TokenResult
import java.util.Date
import java.util.UUID

class JwtService(
    private val jwtSecret: String,
    private val jwtIssuer: String,
    private val jwtAudience: String,
) {
    private val algorithm = Algorithm.HMAC256(jwtSecret)

    private val verifier = JWT
        .require(algorithm)
        .withAudience(jwtAudience)
        .withIssuer(jwtIssuer)
        .build()

    // ── legacy method — kept for existing routes until PR-4 replaces them ────

    fun generateJwtToken(
        credentialId: UUID,
        userId: UUID? = null,
        email: String,
        isAdmin: Boolean = false,
    ): String {
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

        return tokenBuilder.sign(algorithm)
    }

    // ── new access token (15 min, session claims) ────────────────────────────

    fun generateAccessToken(
        session: AuthSession,
        credentialId: UUID,
        email: String,
        userId: UUID? = null,
        isAdmin: Boolean = false,
    ): String {
        val now = System.currentTimeMillis()
        val tokenBuilder = JWT.create()
            .withAudience(jwtAudience)
            .withIssuer(jwtIssuer)
            .withSubject(credentialId.toString())
            .withIssuedAt(Date(now))
            .withExpiresAt(Date(now + ACCESS_TOKEN_TTL_MS))
            .withClaim("credentialId", credentialId.toString())
            .withClaim("sid", session.id.toString())
            .withClaim("ver", session.version)
            .withClaim("scope", session.scope.name)
            .withClaim("email", email)
            .withClaim("isAdmin", isAdmin)

        if (userId != null) {
            tokenBuilder.withClaim("userId", userId.toString())
        }

        return tokenBuilder.sign(algorithm)
    }

    // ── parsing + session validation ─────────────────────────────────────────

    /**
     * Parses and validates a raw JWT.
     * - Cryptographic failure or missing claims → [TokenResult.Invalid]
     * - Expired signature → [TokenResult.Expired]
     * - Session revoked / version mismatch → [TokenResult.SessionRevoked]
     * - All OK → [TokenResult.Valid]
     */
    suspend fun parseAndValidateToken(
        raw: String,
        sessionService: ISessionService,
    ): TokenResult = try {
        val payload = verifier.verify(raw)

        val credentialId = payload.subject
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: return TokenResult.Invalid

        val sidStr = payload.getClaim("sid").asString()
            ?: return TokenResult.Invalid
        val sessionId = runCatching { UUID.fromString(sidStr) }.getOrNull()
            ?: return TokenResult.Invalid

        val ver = payload.getClaim("ver").asInt()
            ?: return TokenResult.Invalid

        val email = payload.getClaim("email").asString()
            ?: return TokenResult.Invalid

        val scopeStr = payload.getClaim("scope").asString()
        val scope = scopeStr?.let { runCatching { SessionScope.valueOf(it) }.getOrNull() }
            ?: return TokenResult.Invalid

        val userId = payload.getClaim("userId").asString()
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

        val isAdmin = payload.getClaim("isAdmin").asBoolean() ?: false

        val session = sessionService.validateSessionForRequest(sessionId, credentialId, ver)
            ?: return TokenResult.SessionRevoked

        TokenResult.Valid(
            sessionId = session.id,
            credentialId = credentialId,
            userId = userId,
            email = email,
            version = ver,
            scope = scope,
            isAdmin = isAdmin,
        )
    } catch (_: TokenExpiredException) {
        TokenResult.Expired
    } catch (_: JWTVerificationException) {
        TokenResult.Invalid
    }

    companion object {
        const val ACCESS_TOKEN_TTL_MS: Long = 15 * 60 * 1000L  // 15 min
        const val EXPIRES_IN_SECONDS: Int = 900
    }
}
