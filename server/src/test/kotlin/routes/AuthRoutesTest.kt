package com.carspotter.routes

import com.carspotter.features.auth.AuthDAO
import com.carspotter.features.auth.AuthProvider
import com.carspotter.features.auth.RefreshTokenGenerator
import com.carspotter.features.auth.GoogleTokenVerifier
import com.carspotter.features.auth.GoogleUser
import com.carspotter.features.auth.dto.AuthResponse
import com.carspotter.features.auth.dto.LoginRequest
import com.carspotter.features.auth.dto.OnboardingStep
import com.carspotter.features.auth.dto.RefreshRequest
import com.carspotter.features.auth.dto.RegisterRequest
import com.carspotter.features.auth.dto.SessionDTO
import com.carspotter.features.auth.dto.UpdatePasswordRequest
import com.carspotter.features.auth.session.AuthSessionDAO
import com.carspotter.features.auth.session.SessionScope
import com.carspotter.features.auth.session.SessionStatus
import com.carspotter.core.error.AuthErrorCode
import com.carspotter.core.error.AuthErrorResponse
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import testutils.TestDatabaseFactory
import testutils.setTestEnv
import testutils.stopKoinSafely
import testutils.testAuthModule

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthRoutesTest {

    /** Fake configurabil pentru testele Google login. */
    private class FakeGoogleVerifier : GoogleTokenVerifier {
        var response: GoogleUser? = null
        override fun verify(googleIdToken: String): GoogleUser? = response
    }

    private val googleVerifier = FakeGoogleVerifier()

    @BeforeAll
    fun setup() {
        setTestEnv()
        TestDatabaseFactory.start()
    }

    @AfterAll
    fun tearDown() {
        TestDatabaseFactory.stop()
    }

    @BeforeEach
    fun clean() {
        TestDatabaseFactory.cleanDatabase()
        googleVerifier.response = null
        stopKoinSafely()
    }

    // ---- helper: rulează un test in-app cu DB real + verifier fake ----
    private fun authTest(block: suspend ApplicationTestBuilder.(io.ktor.client.HttpClient) -> Unit) =
        testApplication {
            application {
                testAuthModule(googleVerifier)
            }
            val client = createClient {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true; isLenient = true })
                }
            }
            block(client)
        }

    // ---- REGISTER ----

    @Test
    fun `POST auth register REGULAR valid returns 201 with token pair and ONBOARDING session`() = authTest { client ->
        val resp = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequest(
                    email = "alice@example.com",
                    password = "Passw0rd!",
                    provider = AuthProvider.REGULAR
                )
            )
        }
        assertEquals(HttpStatusCode.Created, resp.status)
        val body = resp.body<AuthResponse>()
        assertTrue(body.accessToken.isNotBlank())
        assertTrue(body.refreshToken.isNotBlank())
        assertEquals(900, body.expiresIn)
        assertEquals(SessionScope.ONBOARDING.name, body.scope)
        assertEquals(OnboardingStep.PROFILE_REQUIRED, body.onboardingStep)

        val sessions = AuthSessionDAO().listActiveSessions(
            AuthDAO().getCredentialsForLogin("alice@example.com")!!.id!!
        )
        assertEquals(1, sessions.size)
        assertEquals(SessionStatus.ACTIVE, sessions.single().status)
        assertEquals(SessionScope.ONBOARDING, sessions.single().scope)
    }

    @Test
    fun `register with invalid email returns 400`() = authTest { client ->
        val resp = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequest(
                    email = "not-an-email",
                    password = "Passw0rd!",
                    provider = AuthProvider.REGULAR
                )
            )
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `register with missing password returns 400`() = authTest { client ->
        val resp = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequest(
                    email = "alice@example.com",
                    password = null,
                    provider = AuthProvider.REGULAR
                )
            )
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `register with short password returns 400`() = authTest { client ->
        val resp = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequest(
                    email = "alice@example.com",
                    password = "short",
                    provider = AuthProvider.REGULAR
                )
            )
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `register rejects passwords missing any required character class`() = authTest { client ->
        val weakPasswords = listOf(
            "lowercase1!",
            "UPPERCASE1!",
            "NoDigits!!",
            "NoSpecial1"
        )

        weakPasswords.forEachIndexed { index, password ->
            val response = client.post("/api/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(
                    RegisterRequest(
                        email = "weak-$index@example.com",
                        password = password,
                        provider = AuthProvider.REGULAR
                    )
                )
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals(AuthErrorCode.WEAK_PASSWORD, response.body<AuthErrorResponse>().error.code)
        }
    }

    @Test
    fun `register with duplicate email returns 409 EMAIL_TAKEN`() = authTest { client ->
        // first, OK
        val first = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("dup@example.com", "Passw0rd!", AuthProvider.REGULAR))
        }
        assertEquals(HttpStatusCode.Created, first.status)

        val second = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("dup@example.com", "Passw0rd!", AuthProvider.REGULAR))
        }
        assertEquals(HttpStatusCode.Conflict, second.status)
        assertEquals(AuthErrorCode.EMAIL_TAKEN, second.body<AuthErrorResponse>().error.code)
    }

    @Test
    fun `after register password is stored hashed not plain text`() = runTest {
        authTest { client ->
            val resp = client.post("/api/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(RegisterRequest("alice@example.com", "Passw0rd!", AuthProvider.REGULAR))
            }
            assertEquals(HttpStatusCode.Created, resp.status)

            val dao = AuthDAO()
            val stored = dao.getCredentialsForLogin("alice@example.com")
            assertNotNull(stored)
            assertNotEquals("Passw0rd!", stored!!.password)
            assertTrue(stored.password!!.startsWith("$2"), "expected BCrypt hash")
        }
    }

    // ---- LOGIN ----

    @Test
    fun `POST auth login REGULAR valid returns 200 with token pair`() = authTest { client ->
        val registerResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("alice@example.com", "Passw0rd!", AuthProvider.REGULAR))
        }
        val firstRefreshToken = registerResponse.body<AuthResponse>().refreshToken

        val resp = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email = "alice@example.com", password = "Passw0rd!", provider = AuthProvider.REGULAR))
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val body = resp.body<AuthResponse>()
        assertTrue(body.accessToken.isNotBlank())
        assertTrue(body.refreshToken.isNotBlank())
        assertEquals(900, body.expiresIn)
        assertEquals(SessionScope.ONBOARDING.name, body.scope)
        assertEquals(OnboardingStep.PROFILE_REQUIRED, body.onboardingStep)

        val previousSession = AuthSessionDAO().findByRefreshHash(
            RefreshTokenGenerator().hashOf(firstRefreshToken)
        )
        assertEquals(SessionStatus.REVOKED, previousSession?.status)
    }

    @Test
    fun `login with unknown email returns 401`() = authTest { client ->
        val resp = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email = "nobody@example.com", password = "Passw0rd!", provider = AuthProvider.REGULAR))
        }
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `login with wrong password returns 401`() = authTest { client ->
        client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("alice@example.com", "Passw0rd!", AuthProvider.REGULAR))
        }

        val resp = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email = "alice@example.com", password = "WrongPass!", provider = AuthProvider.REGULAR))
        }
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `login with uppercase and spaces in email is normalized`() = authTest { client ->
        client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("alice@example.com", "Passw0rd!", AuthProvider.REGULAR))
        }

        val resp = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email = "  ALICE@Example.COM  ", password = "Passw0rd!", provider = AuthProvider.REGULAR))
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `google login with valid token returns 200`() = authTest { client ->
        googleVerifier.response = GoogleUser(email = "bob@example.com", googleId = "gid-1")

        val resp = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(googleIdToken = "any-fake-token", provider = AuthProvider.GOOGLE))
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val body = resp.body<AuthResponse>()
        assertTrue(body.accessToken.isNotBlank())
        assertTrue(body.refreshToken.isNotBlank())
    }

    @Test
    fun `google login with invalid token returns 401`() = authTest { client ->
        googleVerifier.response = null

        val resp = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(googleIdToken = "bad-token", provider = AuthProvider.GOOGLE))
        }
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `google login for regular account returns 409 PROVIDER_MISMATCH`() = authTest { client ->
        client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("alice@example.com", "Passw0rd!", AuthProvider.REGULAR))
        }
        googleVerifier.response = GoogleUser(email = "alice@example.com", googleId = "gid-regular-email")

        val response = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(googleIdToken = "valid-token", provider = AuthProvider.GOOGLE))
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals(AuthErrorCode.PROVIDER_MISMATCH, response.body<AuthErrorResponse>().error.code)
    }

    @Test
    fun `google login missing token returns 400`() = authTest { client ->
        val resp = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(googleIdToken = null, provider = AuthProvider.GOOGLE))
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    // ---- SESSION MANAGEMENT ----

    @Test
    fun `refresh rotates token pair and consumes previous refresh token`() = authTest { client ->
        val registered = registerAndGetAuthResponse(client)

        val refreshed = client.post("/api/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(RefreshRequest(registered.refreshToken))
        }
        assertEquals(HttpStatusCode.OK, refreshed.status)
        val refreshedBody = refreshed.body<AuthResponse>()
        assertNotEquals(registered.accessToken, refreshedBody.accessToken)
        assertNotEquals(registered.refreshToken, refreshedBody.refreshToken)

        val consumed = client.post("/api/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(RefreshRequest(registered.refreshToken))
        }
        assertEquals(HttpStatusCode.Unauthorized, consumed.status)
        assertEquals(
            AuthErrorCode.REFRESH_TOKEN_CONSUMED,
            consumed.body<AuthErrorResponse>().error.code
        )

        val currentStillWorks = client.post("/api/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(RefreshRequest(refreshedBody.refreshToken))
        }
        assertEquals(HttpStatusCode.OK, currentStillWorks.status)
    }

    @Test
    fun `logout revokes session and refresh token is rejected`() = authTest { client ->
        val registered = registerAndGetAuthResponse(client)

        val logout = client.post("/api/auth/logout") {
            bearerAuth(registered.accessToken)
        }
        assertEquals(HttpStatusCode.NoContent, logout.status)

        val refresh = client.post("/api/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(RefreshRequest(registered.refreshToken))
        }
        assertEquals(HttpStatusCode.Unauthorized, refresh.status)
        assertEquals(AuthErrorCode.SESSION_REVOKED, refresh.body<AuthErrorResponse>().error.code)
    }

    @Test
    fun `sessions returns active current session with device metadata`() = authTest { client ->
        val registered = registerAndGetAuthResponse(
            client,
            deviceId = "install-123",
            deviceName = "Pixel Test"
        )

        val response = client.get("/api/auth/sessions") {
            bearerAuth(registered.accessToken)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val sessions = response.body<List<SessionDTO>>()
        assertEquals(1, sessions.size)
        assertTrue(sessions.single().current)
        assertEquals("install-123", sessions.single().deviceId)
        assertEquals("Pixel Test", sessions.single().deviceName)
    }

    @Test
    fun `logout-all revokes active session`() = authTest { client ->
        val registered = registerAndGetAuthResponse(client)

        val logoutAll = client.post("/api/auth/logout-all") {
            bearerAuth(registered.accessToken)
        }
        assertEquals(HttpStatusCode.NoContent, logoutAll.status)

        val credentialId = AuthDAO().getCredentialsForLogin("alice@example.com")!!.id!!
        assertTrue(AuthSessionDAO().listActiveSessions(credentialId).isEmpty())
    }

    // ---- PUT /auth/password ----

    @Test
    fun `update password with valid token and correct old password returns 200`() = authTest { client ->
        val registered = registerAndGetAuthResponse(client, password = "OldPass!1")

        val resp = client.put("/api/auth/password") {
            contentType(ContentType.Application.Json)
            bearerAuth(registered.accessToken)
            setBody(UpdatePasswordRequest(oldPassword = "OldPass!1", newPassword = "NewPass!2"))
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val rotated = resp.body<AuthResponse>()
        assertNotEquals(registered.accessToken, rotated.accessToken)
        assertNotEquals(registered.refreshToken, rotated.refreshToken)

        val oldAccess = client.get("/api/auth/sessions") {
            bearerAuth(registered.accessToken)
        }
        assertEquals(HttpStatusCode.Unauthorized, oldAccess.status)
        assertEquals(AuthErrorCode.SESSION_REVOKED, oldAccess.body<AuthErrorResponse>().error.code)

        val oldRefresh = client.post("/api/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(RefreshRequest(registered.refreshToken))
        }
        assertEquals(HttpStatusCode.Unauthorized, oldRefresh.status)

        // old password should no longer work
        val oldLogin = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email = "alice@example.com", password = "OldPass!1", provider = AuthProvider.REGULAR))
        }
        assertEquals(HttpStatusCode.Unauthorized, oldLogin.status)

        // new password should work
        val newLogin = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email = "alice@example.com", password = "NewPass!2", provider = AuthProvider.REGULAR))
        }
        assertEquals(HttpStatusCode.OK, newLogin.status)
    }

    @Test
    fun `update password without token returns 401`() = authTest { client ->
        val resp = client.put("/api/auth/password") {
            contentType(ContentType.Application.Json)
            setBody(UpdatePasswordRequest(oldPassword = "x", newPassword = "NewPass!2"))
        }
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    // ---- DELETE /auth/account ----

    @Test
    fun `delete account with valid token returns 200 and removes credential from DB`() = authTest { client ->
        val token = registerAndGetToken(client, "alice@example.com", "Passw0rd!")

        val resp = client.delete("/api/auth/account") {
            bearerAuth(token)
        }
        assertEquals(HttpStatusCode.OK, resp.status)

        val dao = AuthDAO()
        val stored = dao.getCredentialsForLogin("alice@example.com")
        assertTrue(stored == null, "credential should have been deleted")

        val afterDelete = client.get("/api/auth/sessions") {
            bearerAuth(token)
        }
        assertEquals(HttpStatusCode.Unauthorized, afterDelete.status)
        assertEquals(AuthErrorCode.SESSION_REVOKED, afterDelete.body<AuthErrorResponse>().error.code)
    }

    @Test
    fun `delete account without token returns 401`() = authTest { client ->
        val resp = client.delete("/api/auth/account")
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    // ---- helpers ----

    private suspend fun registerAndGetToken(
        client: io.ktor.client.HttpClient,
        email: String,
        password: String
    ): String {
        val resp = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email, password, AuthProvider.REGULAR))
        }
        assertEquals(HttpStatusCode.Created, resp.status)
        return resp.body<AuthResponse>().accessToken
    }

    private suspend fun registerAndGetAuthResponse(
        client: io.ktor.client.HttpClient,
        deviceId: String? = null,
        deviceName: String? = null,
        password: String = "Passw0rd!",
    ): AuthResponse {
        val response = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterRequest(
                    email = "alice@example.com",
                    password = password,
                    provider = AuthProvider.REGULAR,
                    deviceId = deviceId,
                    deviceName = deviceName,
                )
            )
        }
        assertEquals(HttpStatusCode.Created, response.status)
        return response.body()
    }
}
