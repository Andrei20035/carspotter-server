package service

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.carspotter.features.auth.JwtService
import com.carspotter.features.auth.session.AuthSession
import com.carspotter.features.auth.session.ISessionService
import com.carspotter.features.auth.session.SessionScope
import com.carspotter.features.auth.session.SessionStatus
import com.carspotter.features.auth.session.TokenResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Date
import java.util.UUID

class JwtServiceTest {
    private val secret = "test-secret-that-is-long-enough"
    private val issuer = "carspotter-test"
    private val audience = "carspotter-client"
    private val jwtService = JwtService(secret, issuer, audience)

    private fun session(
        credentialId: UUID = UUID.randomUUID(),
        scope: SessionScope = SessionScope.ONBOARDING,
        userId: UUID? = null,
        version: Int = 3,
    ) = AuthSession(
        id = UUID.randomUUID(),
        credentialId = credentialId,
        userId = userId,
        version = version,
        scope = scope,
        refreshTokenHash = "a".repeat(64),
        prevTokenHash = null,
        prevRotatedAt = null,
        status = SessionStatus.ACTIVE,
        revokedReason = null,
        deviceId = null,
        deviceName = null,
        userAgent = null,
        ipAddress = null,
        createdAt = Instant.now(),
        lastUsedAt = Instant.now(),
        idleExpiresAt = Instant.now().plusSeconds(3600),
        absoluteExpiresAt = Instant.now().plusSeconds(7200),
    )

    @Test
    fun `generateAccessToken emits v2 claims and 15 minute TTL`() {
        val credentialId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val session = session(
            credentialId = credentialId,
            scope = SessionScope.FULL,
            userId = userId,
            version = 7,
        )

        val token = jwtService.generateAccessToken(
            session = session,
            credentialId = credentialId,
            email = "alice@example.com",
            userId = userId,
            isAdmin = true,
        )
        val decoded = JWT.decode(token)

        assertEquals(issuer, decoded.issuer)
        assertTrue(decoded.audience.contains(audience))
        assertEquals(credentialId.toString(), decoded.subject)
        assertEquals(credentialId.toString(), decoded.getClaim("credentialId").asString())
        assertEquals(session.id.toString(), decoded.getClaim("sid").asString())
        assertEquals(7, decoded.getClaim("ver").asInt())
        assertEquals(SessionScope.FULL.name, decoded.getClaim("scope").asString())
        assertEquals(userId.toString(), decoded.getClaim("userId").asString())
        assertEquals("alice@example.com", decoded.getClaim("email").asString())
        assertEquals(true, decoded.getClaim("isAdmin").asBoolean())
        assertTrue(decoded.issuedAt.time <= decoded.expiresAt.time)
        assertTrue(
            (decoded.expiresAt.time - decoded.issuedAt.time) in
                (JwtService.ACCESS_TOKEN_TTL_MS - 1_000)..(JwtService.ACCESS_TOKEN_TTL_MS + 1_000)
        )
    }

    @Test
    fun `generateAccessToken omits userId for onboarding`() {
        val credentialId = UUID.randomUUID()
        val session = session(credentialId = credentialId)

        val decoded = JWT.decode(
            jwtService.generateAccessToken(session, credentialId, "alice@example.com")
        )

        assertNull(decoded.getClaim("userId").asString())
        assertEquals(SessionScope.ONBOARDING.name, decoded.getClaim("scope").asString())
    }

    @Test
    fun `parseAndValidateToken returns Valid after session validation`() = runTest {
        val credentialId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val session = session(credentialId, SessionScope.FULL, userId, version = 4)
        val service = mockk<ISessionService>()
        coEvery {
            service.validateSessionForRequest(session.id, credentialId, 4)
        } returns session
        val raw = jwtService.generateAccessToken(
            session, credentialId, "alice@example.com", userId, isAdmin = true
        )

        val result = jwtService.parseAndValidateToken(raw, service)

        val valid = assertInstanceOf(TokenResult.Valid::class.java, result)
        assertEquals(session.id, valid.sessionId)
        assertEquals(credentialId, valid.credentialId)
        assertEquals(userId, valid.userId)
        assertEquals(SessionScope.FULL, valid.scope)
        assertEquals(true, valid.isAdmin)
        coVerify(exactly = 1) {
            service.validateSessionForRequest(session.id, credentialId, 4)
        }
    }

    @Test
    fun `parseAndValidateToken returns SessionRevoked when session validation fails`() = runTest {
        val credentialId = UUID.randomUUID()
        val session = session(credentialId = credentialId)
        val service = mockk<ISessionService>()
        coEvery {
            service.validateSessionForRequest(session.id, credentialId, session.version)
        } returns null
        val raw = jwtService.generateAccessToken(session, credentialId, "alice@example.com")

        assertEquals(TokenResult.SessionRevoked, jwtService.parseAndValidateToken(raw, service))
    }

    @Test
    fun `parseAndValidateToken returns Expired for expired token`() = runTest {
        val credentialId = UUID.randomUUID()
        val raw = JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withSubject(credentialId.toString())
            .withClaim("credentialId", credentialId.toString())
            .withClaim("sid", UUID.randomUUID().toString())
            .withClaim("ver", 1)
            .withClaim("scope", SessionScope.ONBOARDING.name)
            .withClaim("email", "alice@example.com")
            .withExpiresAt(Date(System.currentTimeMillis() - 1_000))
            .sign(Algorithm.HMAC256(secret))

        assertEquals(
            TokenResult.Expired,
            jwtService.parseAndValidateToken(raw, mockk(relaxed = true)),
        )
    }

    @Test
    fun `parseAndValidateToken returns Invalid for malformed or incomplete token`() = runTest {
        val incomplete = JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withExpiresAt(Date(System.currentTimeMillis() + 60_000))
            .sign(Algorithm.HMAC256(secret))

        assertEquals(
            TokenResult.Invalid,
            jwtService.parseAndValidateToken(incomplete, mockk(relaxed = true)),
        )
        assertEquals(
            TokenResult.Invalid,
            jwtService.parseAndValidateToken("not-a-jwt", mockk(relaxed = true)),
        )
    }
}
