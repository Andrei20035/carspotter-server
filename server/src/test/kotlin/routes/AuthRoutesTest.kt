package com.carspotter.routes

import com.carspotter.features.auth.AuthDAO
import com.carspotter.features.auth.AuthProvider
import com.carspotter.features.auth.GoogleTokenVerifier
import com.carspotter.features.auth.GoogleUser
import com.carspotter.features.auth.dto.AuthResponse
import com.carspotter.features.auth.dto.LoginRequest
import com.carspotter.features.auth.dto.OnboardingStep
import com.carspotter.features.auth.dto.RegisterRequest
import com.carspotter.features.auth.dto.UpdatePasswordRequest
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
    fun `POST auth register REGULAR valid returns 201 with token and PROFILE_REQUIRED`() = authTest { client ->
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
        assertTrue(body.token.isNotBlank())
        assertEquals(OnboardingStep.PROFILE_REQUIRED, body.onboardingStep)
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
    fun `register with duplicate email returns 400`() = authTest { client ->
        // first, OK
        val first = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("dup@example.com", "Passw0rd!", AuthProvider.REGULAR))
        }
        assertEquals(HttpStatusCode.Created, first.status)

        // second, should fail because service throws IllegalArgumentException → route returns 400
        val second = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("dup@example.com", "Passw0rd!", AuthProvider.REGULAR))
        }
        assertEquals(HttpStatusCode.BadRequest, second.status)
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
    fun `POST auth login REGULAR valid returns 200 with token`() = authTest { client ->
        client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("alice@example.com", "Passw0rd!", AuthProvider.REGULAR))
        }

        val resp = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email = "alice@example.com", password = "Passw0rd!", provider = AuthProvider.REGULAR))
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val body = resp.body<AuthResponse>()
        assertTrue(body.token.isNotBlank())
        assertEquals(OnboardingStep.COMPLETED, body.onboardingStep)
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
        assertTrue(body.token.isNotBlank())
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
    fun `google login missing token returns 400`() = authTest { client ->
        val resp = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(googleIdToken = null, provider = AuthProvider.GOOGLE))
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    // ---- PUT /auth/password ----

    @Test
    fun `update password with valid token and correct old password returns 200`() = authTest { client ->
        val token = registerAndGetToken(client, "alice@example.com", "OldPass!1")

        val resp = client.put("/api/auth/password") {
            contentType(ContentType.Application.Json)
            bearerAuth(token)
            setBody(UpdatePasswordRequest(oldPassword = "OldPass!1", newPassword = "NewPass!2"))
        }
        assertEquals(HttpStatusCode.OK, resp.status)

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
        return resp.body<AuthResponse>().token
    }
}