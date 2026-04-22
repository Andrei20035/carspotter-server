package routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.carspotter.config.configureSerialization
import com.carspotter.features.auth.dto.AuthDTO
import com.carspotter.features.auth.AuthProvider
import com.carspotter.features.auth.IAuthService
import com.carspotter.features.auth.JwtService
import com.carspotter.features.auth.authRoutes
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import java.util.*


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthRoutesTest : KoinTest {

    private lateinit var authCredentialService: IAuthService
    private lateinit var jwtService: JwtService

    @BeforeAll
    fun setup() {
        authCredentialService = mockk()
        jwtService = mockk()

        every { jwtService.generateJwtToken(any(), any(), any(), any()) } returns mapOf("token" to "token")
    }

    @BeforeEach
    fun setupKoin() {
        startKoin {
            modules(
                module {
                    single { authCredentialService }
                    single { jwtService }
                }
            )
        }
    }

    // Helper function to configure the application for testing
    private fun Application.configureTestApplication() {
        System.setProperty("JWT_SECRET", "test-secret-key")

        configureSerialization()

        install(Authentication) {
            jwt("jwt") {
                realm = "Test Server"
                verifier(
                    JWT
                        .require(Algorithm.HMAC256("test-secret-key"))
                        .build()
                )
                validate { credential ->
                    val credentialIdString = credential.payload.getClaim("credentialId").asString()
                    if (credentialIdString != null) {
                        try {
                            UUID.fromString(credentialIdString)
                            JWTPrincipal(credential.payload)
                        } catch (e: IllegalArgumentException) {
                            null
                        }
                    } else {
                        null
                    }
                }
            }
        }

        routing {
            authRoutes()
        }
    }

    @AfterEach
    fun tearDownKoin() {
        stopKoin()
        clearAllMocks()
    }

    @Test
    fun `regular login with valid credentials returns token`() = testApplication {
        val email = "test@example.com"
        val password = "password123"
        val provider = AuthProvider.REGULAR
        val credentialId = UUID.randomUUID()

        val mockCredential = AuthDTO(
            id = credentialId,
            email = email,
            provider = provider,
        )

        coEvery { authCredentialService.regularLogin(email, password) } returns mockCredential
        coEvery { jwtService.generateJwtToken(credentialId, null, email) } returns mapOf("token" to "fake.jwt.token")

        application {
            configureTestApplication()
        }

        val response = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"$password","provider":"REGULAR"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val responseBody = response.bodyAsText()
        assertTrue(responseBody.contains("token"))

        coVerify(exactly = 1) { authCredentialService.regularLogin(email, password) }
        coVerify(exactly = 1) { jwtService.generateJwtToken(credentialId, null, email) }

    }

    @Test
    fun `regular login with invalid credentials returns unauthorized`() = testApplication {
        val email = "test@example.com"
        val password = "password123"
        val provider = AuthProvider.REGULAR
        val googleId = "1234"
        val credentialId = UUID.randomUUID()

        val mockCredential = AuthDTO(
            id = credentialId,
            email = email,
            provider = provider,
        )

        coEvery { authCredentialService.regularLogin(email, password) } returns null

        application {
            configureTestApplication()
        }

        val response = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"$password","googleId":"$googleId","provider":"REGULAR"}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)

        coVerify(exactly = 1) { authCredentialService.regularLogin(email, password) }
    }

    @Test
    fun `regular login with invalid email format returns bad request`() = testApplication {
        val email = "invalid-email"
        val password = "password123"

        application {
            configureTestApplication()
        }

        val response = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"$password"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)

        coVerify(exactly = 0) { authCredentialService.regularLogin(any(), any()) }
    }

    @Test
    fun `google login with valid credentials returns token`() = testApplication {
        val email = "test@example.com"
        val googleId = "google123"
        val credentialId = UUID.randomUUID()
        val provider = AuthProvider.GOOGLE

        val mockCredential = AuthDTO(
            id = credentialId,
            email = email,
            provider = provider,
        )

        coEvery { authCredentialService.googleLogin(email, googleId) } returns mockCredential
        coEvery { jwtService.generateJwtToken(credentialId, null, email) } returns mapOf("token" to "fake.jwt.token")

        application {
            configureTestApplication()
        }

        val response = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","googleIdToken":"$googleId","provider":"GOOGLE"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val responseBody = response.bodyAsText()
        println(responseBody)
        assertTrue(responseBody.contains("token"))

        coVerify(exactly = 1) { authCredentialService.googleLogin(email, googleId) }
        coVerify(exactly = 1) { jwtService.generateJwtToken(credentialId, null, email) }
    }

    @Test
    fun `register with valid data returns created status`() = testApplication {
        val email = "newuser@example.com"
        val password = "newpassword123"
        val provider = AuthProvider.REGULAR
        val credentialId = UUID.randomUUID()

        coEvery {
            authCredentialService.createCredentials(match {
                it.email == email && it.password == password && it.provider == provider
            })
        } returns credentialId

        val token = mapOf("token" to "mocked-jwt-token")

        coEvery {
            jwtService.generateJwtToken(
                credentialId,
                null,
                email,
                false
            )
        } returns token

        application {
            configureTestApplication()
        }

        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"$password","provider":"REGULAR"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)

        coVerify(exactly = 1) {
            authCredentialService.createCredentials(match {
                it.email == email && it.password == password && it.provider == provider
            })
        }
    }

    @Test
    fun `update password with valid token returns success`() = testApplication {
        val credentialId = UUID.randomUUID()
        val newPassword = "newpassword456"

        System.setProperty("JWT_SECRET", "test-secret-key")

        coEvery { authCredentialService.updatePassword(credentialId, newPassword) } returns 1

        application {
            configureTestApplication()
        }

        val token = JWT.create()
            .withClaim("credentialId", credentialId.toString())
            .withExpiresAt(Date(System.currentTimeMillis() + 60000))
            .sign(Algorithm.HMAC256("test-secret-key"))

        val response = client.put("/auth/password") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"newPassword":"$newPassword"}""")
        }

        // Assert
        assertEquals(HttpStatusCode.OK, response.status)

        val expectedJson = Json.parseToJsonElement("""{"message":"Password updated successfully"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { authCredentialService.updatePassword(credentialId, newPassword) }
    }

    @Test
    fun `delete account should return success`() = testApplication {
        val credentialId = UUID.randomUUID()

        System.setProperty("JWT_SECRET", "test-secret-key")

        coEvery { authCredentialService.deleteCredentials(credentialId) } returns 1

        every { jwtService.generateJwtToken(credentialId, null, "amrusu2@gmail.com", false) } returns mapOf("token" to "token")

        application {
            configureTestApplication()
        }

        val token = JWT.create()
            .withClaim("credentialId", credentialId.toString())
            .withExpiresAt(Date(System.currentTimeMillis() + 60000))
            .sign(Algorithm.HMAC256("test-secret-key"))

        val response = client.delete("/auth/account") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val expectedJson = Json.parseToJsonElement("""{"message":"Account deleted successfully"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { authCredentialService.deleteCredentials(credentialId) }
    }
}
