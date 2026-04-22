package routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.carspotter.config.configureSerialization
import com.carspotter.features.auth.AuthProvider
import com.carspotter.features.auth.AuthService
import com.carspotter.features.auth.GoogleTokenVerifier
import com.carspotter.features.auth.IAuthService
import com.carspotter.features.auth.AuthTable
import com.carspotter.core.di.appModule
import com.carspotter.di.daoModule
import com.carspotter.di.repositoryModule
import com.carspotter.di.serviceModule
import com.carspotter.features.auth.authRoutes
import data.testutils.FakeGoogleTokenVerifier
import data.testutils.SchemaSetup
import data.testutils.TestDatabase
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthRoutesIntegrationTest : KoinTest {

    @BeforeAll
    fun setupDatabase() {
        TestDatabase.start()
        Database.connect(
            url = TestDatabase.postgresContainer.jdbcUrl,
            driver = "org.postgresql.Driver",
            user = TestDatabase.postgresContainer.username,
            password = TestDatabase.postgresContainer.password
        )

        startKoin {
            modules(daoModule, repositoryModule, serviceModule, appModule,
                module {
                    single<GoogleTokenVerifier> { FakeGoogleTokenVerifier() }
                    single<IAuthService> { AuthService(get(), get()) }
                })
        }

        SchemaSetup.createAuthCredentialsTableWithConstraint(AuthTable)
    }

    @BeforeEach
    fun clearDatabase() {
        transaction {
            AuthTable.deleteAll()
        }
    }

    private fun Application.configureTestApplication() {
//        val jwtSecret = System.getenv("JWT_SECRET") ?: throw IllegalStateException("JWT_SECRET environment variable is not set")
        val jwtSecret = "test-jwt-secret"
        configureSerialization()

        install(Authentication) {
            jwt("jwt") {
                realm = "Test Server"
                verifier(
                    JWT
                        .require(Algorithm.HMAC256(jwtSecret))
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
                challenge { _, _ ->
                    call.respond(HttpStatusCode.Unauthorized, "Token is not valid or has expired")
                }
            }
        }

        routing {
            authRoutes()
        }
    }

    @Test
    fun `register and login with regular credentials`() = testApplication {
        val email = "test@example.com"
        val password = "password123"
        val provider = AuthProvider.REGULAR

        application {
            configureTestApplication()
        }

        val registerResponse = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"$password","provider":"${provider.name}"}""")
        }

        assertEquals(HttpStatusCode.OK, registerResponse.status)

        // Login with the registered credentials
        val loginResponse = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"$password","provider":"${provider.name}"}""")
        }

        assertEquals(HttpStatusCode.OK, loginResponse.status)
        val responseBody = loginResponse.bodyAsText()
        assertTrue(responseBody.contains("token"))

        // Parse the token from the response
        val jsonObject = Json.parseToJsonElement(responseBody).jsonObject
        val token = jsonObject["token"]?.jsonPrimitive?.content
        assertTrue(!token.isNullOrEmpty(), "Token should not be null or empty")
    }

    @Test
    fun `login with google credentials`() = testApplication {
        val email = "google@example.com"
        val googleId = "gid1"
        val provider = AuthProvider.GOOGLE

        application {
            configureTestApplication()
        }

        val loginResponse = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","googleIdToken":"$googleId","provider":"${provider.name}"}""")
        }

        assertEquals(HttpStatusCode.OK, loginResponse.status)

        val responseBody = loginResponse.bodyAsText()
        assertTrue(responseBody.contains("token"))
    }

    @Test
    fun `update password with valid token`() = testApplication {
        val email = "test@example.com"
        val password = "password123"
        val newPassword = "newpassword456"
        val provider = AuthProvider.REGULAR

        application {
            configureTestApplication()
        }

        val registerResponse = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"$password","provider":"${provider.name}"}""")
        }

        assertEquals(HttpStatusCode.OK, registerResponse.status)

        val loginResponse = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"$password","provider":"${provider.name}"}""")
        }

        assertEquals(HttpStatusCode.OK, loginResponse.status)
        val responseBody = loginResponse.bodyAsText()
        val jsonObject = Json.parseToJsonElement(responseBody).jsonObject
        val token = jsonObject["token"]?.jsonPrimitive?.content
        assertTrue(!token.isNullOrEmpty(), "Token should not be null or empty")

        val updateResponse = client.put("/auth/password") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"newPassword":"$newPassword"}""")
        }

        assertEquals(HttpStatusCode.OK, updateResponse.status)
        val updateResponseBody = updateResponse.bodyAsText()
        val updateJsonObject = Json.parseToJsonElement(updateResponseBody).jsonObject
        assertEquals("Password updated successfully", updateJsonObject["message"]?.jsonPrimitive?.content)

        val newLoginResponse = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"$newPassword","provider":"${provider.name}"}""")
        }

        assertEquals(HttpStatusCode.OK, newLoginResponse.status)
    }

    @Test
    fun `delete account with valid token`() = testApplication {
        val email = "test@example.com"
        val password = "password123"
        val provider = AuthProvider.REGULAR

        application {
            configureTestApplication()
        }

        val registerResponse = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"$password","provider":"${provider.name}"}""")
        }

        assertEquals(HttpStatusCode.OK, registerResponse.status)

        val loginResponse = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"$password","provider":"${provider.name}"}""")
        }

        assertEquals(HttpStatusCode.OK, loginResponse.status)
        val responseBody = loginResponse.bodyAsText()
        val jsonObject = Json.parseToJsonElement(responseBody).jsonObject
        val token = jsonObject["token"]?.jsonPrimitive?.content
        assertTrue(!token.isNullOrEmpty(), "Token should not be null or empty")

        val deleteResponse = client.delete("/auth/account") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, deleteResponse.status)
        val deleteResponseBody = deleteResponse.bodyAsText()
        val deleteJsonObject = Json.parseToJsonElement(deleteResponseBody).jsonObject
        assertEquals("Account deleted successfully", deleteJsonObject["message"]?.jsonPrimitive?.content)

        val failedLoginResponse = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"$password","provider":"${provider.name}"}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, failedLoginResponse.status)
    }

    @AfterAll
    fun tearDown() {
        transaction {
            SchemaUtils.drop(AuthTable)
        }
        stopKoin()
    }
}