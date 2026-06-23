package com.carspotter.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.carspotter.features.auth.AuthProvider
import com.carspotter.features.auth.dto.AuthResponse
import com.carspotter.features.auth.dto.LoginRequest
import com.carspotter.features.auth.dto.RegisterRequest
import com.carspotter.features.auth.dto.UpdatePasswordRequest
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import testutils.TestDatabaseFactory
import testutils.TestEnv
import testutils.setTestEnv
import testutils.stopKoinSafely
import testutils.testAuthModule
import testutils.testPostModule
import java.util.*

/**
 * Testele de caracterizare rămase după PR-4.
 * Așteptările pentru register/login au fost actualizate la perechea de tokenuri
 * și TTL-ul de 15 minute; vulnerabilitățile corectate în PR-urile ulterioare
 * rămân documentate separat.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthRoutesCharacterizationTest {

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
        stopKoinSafely()
    }

    private fun authTest(block: suspend ApplicationTestBuilder.(io.ktor.client.HttpClient) -> Unit) =
        testApplication {
            application { testAuthModule(object : com.carspotter.features.auth.GoogleTokenVerifier {
                override fun verify(googleIdToken: String): com.carspotter.features.auth.GoogleUser? = null
            }) }
            val client = createClient {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true }) }
            }
            block(client)
        }

    private fun postTest(block: suspend ApplicationTestBuilder.(io.ktor.client.HttpClient) -> Unit) =
        testApplication {
            application { testPostModule() }
            val client = createClient {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true }) }
            }
            block(client)
        }

    // ── Test 1 ───────────────────────────────────────────────────────────────
    @Test
    fun `register returns access and refresh token pair`() = authTest { client ->
        val resp = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("alice@example.com", "Passw0rd!", AuthProvider.REGULAR))
        }
        assertEquals(HttpStatusCode.Created, resp.status)

        val body = resp.body<AuthResponse>()
        assertTrue(body.accessToken.isNotBlank())
        assertTrue(body.refreshToken.isNotBlank())
        val raw = resp.body<String>()
        assertTrue(raw.contains("refreshToken"))
        assertTrue(raw.contains("accessToken"))
        assertFalse(raw.contains("\"token\""))
    }

    // ── Test 2 ───────────────────────────────────────────────────────────────
    @Test
    fun `register emits JWT with 15 minute TTL`() = authTest { client ->
        val resp = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("alice@example.com", "Passw0rd!", AuthProvider.REGULAR))
        }
        assertEquals(HttpStatusCode.Created, resp.status)

        val token = resp.body<AuthResponse>().accessToken
        val decoded = JWT.decode(token)
        val exp = decoded.expiresAt?.time ?: fail("token must have exp")
        val ttlSecondsFromNow = (exp - System.currentTimeMillis()) / 1000

        assertTrue(ttlSecondsFromNow in 840L..900L,
            "access JWT TTL must be ~900s, got $ttlSecondsFromNow s remaining")
    }

    // ── Test 3 ───────────────────────────────────────────────────────────────
    @Test
    fun `login emits JWT with 15 minute TTL and refresh token`() = authTest { client ->
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
        val token = body.accessToken
        val decoded = JWT.decode(token)
        val ttlSecondsFromNow = (decoded.expiresAt.time - System.currentTimeMillis()) / 1000
        assertTrue(ttlSecondsFromNow in 840L..900L,
            "login JWT TTL must be ~900s, got $ttlSecondsFromNow s remaining")

        val raw = resp.body<String>()
        assertTrue(raw.contains("refreshToken"))
    }

    // ── Test 4 ───────────────────────────────────────────────────────────────
    // [PR-6] După schimbarea parolei, sesiunea curentă este rotită,
    // iar access tokenul vechi devine invalid prin version mismatch.
    @Test
    fun `old token is rejected after password change`() = authTest { client ->
        // Înregistrare + obținere token inițial
        val registerResp = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("alice@example.com", "OldPass!1", AuthProvider.REGULAR))
        }
        val oldToken = registerResp.body<AuthResponse>().accessToken

        // Schimbare parolă
        val changeResp = client.put("/api/auth/password") {
            contentType(ContentType.Application.Json)
            bearerAuth(oldToken)
            setBody(UpdatePasswordRequest("OldPass!1", "NewPass!2"))
        }
        assertEquals(HttpStatusCode.OK, changeResp.status)

        val reuse = client.put("/api/auth/password") {
            contentType(ContentType.Application.Json)
            bearerAuth(oldToken)
            setBody(UpdatePasswordRequest("OldPass!1", "AnotherPass!3"))
        }
        assertEquals(
            HttpStatusCode.Unauthorized, reuse.status,
            "old token must be rejected after password-change session rotation"
        )
    }

    // ── Test 5 ───────────────────────────────────────────────────────────────
    // [PR-6] Ștergerea credentialului șterge în cascadă sesiunea,
    // iar access tokenul vechi este respins la următorul request.
    @Test
    fun `old token is rejected after account deletion`() = authTest { client ->
        val registerResp = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("alice@example.com", "Passw0rd!", AuthProvider.REGULAR))
        }
        val token = registerResp.body<AuthResponse>().accessToken

        // Ștergere cont
        val deleteResp = client.delete("/api/auth/account") {
            bearerAuth(token)
        }
        assertEquals(HttpStatusCode.OK, deleteResp.status)

        val afterDelete = client.delete("/api/auth/account") {
            bearerAuth(token)
        }
        assertEquals(
            HttpStatusCode.Unauthorized, afterDelete.status,
            "deleted account token must be rejected after its session is cascade-deleted"
        )
    }

    // ── Test 6 ───────────────────────────────────────────────────────────────
    // [BEHAVIOR] GET /posts/feed cu un token EXPIRAT returnează 401, nu 200 anonim.
    // authenticate("jwt", optional=true) în Ktor lasă să treacă absența tokenului,
    // dar un token prezent-și-expirat declanșează `challenge` → 401.
    // Remediat în PR-8 (parsing manual).
    @Test
    fun `feed with expired token returns 401 not anonymous 200`() = postTest { client ->
        val expiredToken = JWT.create()
            .withAudience(TestEnv.JWT_AUDIENCE)
            .withIssuer(TestEnv.JWT_ISSUER)
            .withClaim("credentialId", UUID.randomUUID().toString())
            .withClaim("email", "alice@example.com")
            .withClaim("isAdmin", false)
            // exp în trecut: acum − 1 oră
            .withExpiresAt(Date(System.currentTimeMillis() - 3_600_000L))
            .sign(Algorithm.HMAC256(TestEnv.JWT_SECRET))

        val resp = client.get("/api/posts/feed") {
            bearerAuth(expiredToken)
        }

        // [BEHAVIOR] Token expirat → 401 (nu fallback anonim 200)
        assertEquals(
            HttpStatusCode.Unauthorized, resp.status,
            "expired token on optional-auth feed must return 401, not anonymous 200"
        )
    }

    // ── Test 7 ───────────────────────────────────────────────────────────────
    // [BEHAVIOR] GET /posts/feed fără token returnează 200 (feed anonim funcționează).
    // Confirmat că absența tokenului nu cauzează probleme.
    @Test
    fun `feed without token returns 200 anonymous`() = postTest { client ->
        val resp = client.get("/api/posts/feed")
        assertEquals(HttpStatusCode.OK, resp.status)
    }
}
