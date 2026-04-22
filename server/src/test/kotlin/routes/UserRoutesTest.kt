package routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.carspotter.config.configureSerialization
import com.carspotter.features.user.dto.UserDTO
import com.carspotter.features.user.dto.CreateUserRequest
import com.carspotter.features.user.dto.UpdateProfilePictureRequest
import com.carspotter.features.auth.JwtService
import com.carspotter.features.user.IUserService
import com.carspotter.features.user.userRoutes
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.*
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import java.time.LocalDate
import java.util.*
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserRoutesTest : KoinTest {

    private lateinit var userService: IUserService
    private lateinit var jwtService: JwtService

    @BeforeAll
    fun setup() {
        userService = mockk()
        jwtService = mockk()
    }

    @BeforeEach
    fun setupKoin() {
        startKoin {
            modules(
                module {
                    single { userService }
                    single { jwtService }
                }
            )
        }
    }

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
                    val credentialIdString = credential.payload.getClaim("userId").asString()
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
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or missing JWT token"))
                }
            }
            jwt("admin") {
                realm = "Test Server"
                verifier(
                    JWT
                        .require(Algorithm.HMAC256("test-secret-key"))
                        .build()
                )
                validate { credential ->
                    if (credential.payload.getClaim("userId").asInt() != null &&
                        credential.payload.getClaim("isAdmin").asBoolean() == true) {
                        JWTPrincipal(credential.payload)
                    } else {
                        null
                    }
                }
                challenge { _, _ ->
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin access required"))
                }
            }
        }

        routing {
            userRoutes()
        }
    }

    @AfterEach
    fun tearDownKoin() {
        stopKoin()
        clearAllMocks()
    }

    @Test
    fun `GET me returns 401 when no jwt provided`() = testApplication {
        application {
            configureTestApplication()
        }

        val response = client.get("/user/me")

        assertEquals(HttpStatusCode.Unauthorized, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"Invalid or missing JWT token"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `GET me returns 404 when user not found`() = testApplication {
        val userId = UUID.randomUUID()
        val credentialId = UUID.randomUUID()

        coEvery { userService.getUserById(userId) } returns null

        application {
            configureTestApplication()
        }

        val token = createTestToken(userId, email = "test@yahoo.com", credentialId = credentialId)

        val response = client.get("/user/me") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"User not found"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { userService.getUserById(userId) }
    }

    @Test
    fun `GET me returns 200 with user data when user found`() = testApplication {
        val userId = UUID.randomUUID()
        val credentialId = UUID.randomUUID()

        val user = UserDTO(
            id = userId,
            fullName = "Test User",
            phoneNumber = "0712453678",
            username = "testuser",
            profilePicturePath = "path/to/picture.jpg",
            birthDate = LocalDate.of(1990, 1, 1),
            country = "USA"
        )

        coEvery { userService.getUserById(userId) } returns user

        application {
            configureTestApplication()
        }

        val token = createTestToken(userId, email = "test@yahoo.com", credentialId = credentialId)

        val response = client.get("/user/me") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val expectedJson = Json.encodeToJsonElement(user)
        val actualJson = Json.parseToJsonElement(response.bodyAsText())

        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { userService.getUserById(userId) }
    }

    @Test
    fun `GET all returns 403 when user is not admin`() = testApplication {
        application {
            configureTestApplication()
        }

        val userId = UUID.randomUUID()
        val credentialId = UUID.randomUUID()

        val token = createTestToken(userId, false, email = "test@yahoo.com", credentialId = credentialId)

        val response = client.get("/user/all") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"Admin access required"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `GET all returns 200 with all users when user is admin`() = testApplication {
        val userId1 = UUID.randomUUID()
        val userId2 = UUID.randomUUID()
        val credentialId = UUID.randomUUID()

        val users = listOf(
            UserDTO(
                id = userId1,
                fullName = "First1 Last1",
                phoneNumber = "0712453678",
                username = "user1", 
                profilePicturePath = null, 
                birthDate = LocalDate.of(1990, 1, 1), 
                country = "USA"
            ),
            UserDTO(
                id = userId2,
                fullName = "First2 Last2",
                phoneNumber = "0712453678",
                username = "user2", 
                profilePicturePath = "path/to/picture.jpg", 
                birthDate = LocalDate.of(1992, 2, 2), 
                country = "Canada"
            )
        )

        coEvery { userService.getAllUsers() } returns users

        application {
            configureTestApplication()
        }

        val token = createTestToken(userId1, true, email = "test@yahoo.com", credentialId = credentialId)

        val response = client.get("/user/all") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val expectedJson = Json.encodeToJsonElement(users)
        val actualJson = Json.parseToJsonElement(response.bodyAsText())

        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { userService.getAllUsers() }
    }

    @Test
    fun `GET by-username returns 404 when username is missing`() = testApplication {
        val userId = UUID.randomUUID()
        val credentialId = UUID.randomUUID()

        application {
            configureTestApplication()
        }

        val token = createTestToken(userId = userId, email = "test@yahoo.com", credentialId = credentialId)

        val response = client.get("/user/by-username/") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)

        // No response body to check for 404
    }

    @Test
    fun `GET by-username returns 200 with matching users`() = testApplication {
        val username1 = "test1"
        val username2 = "tester"
        val userId1 = UUID.randomUUID()
        val userId2 = UUID.randomUUID()
        val credentialId1 = UUID.randomUUID()

        val users = listOf(
            UserDTO(
                id = userId1,
                fullName = "Test User",
                phoneNumber = "0712453678",
                username = username1,
                profilePicturePath = null, 
                birthDate = LocalDate.of(1990, 1, 1), 
                country = "USA"
            ),
            UserDTO(
                id = userId2,
                fullName = "Tester User",
                phoneNumber = "0712453678",
                username = username2,
                profilePicturePath = null, 
                birthDate = LocalDate.of(1992, 2, 2), 
                country = "Canada"
            )
        )

        coEvery { userService.getUserByUsername(username1) } returns users

        application {
            configureTestApplication()
        }

        val token = createTestToken(userId = userId1, email = "test@yahoo.com", credentialId = credentialId1)

        val response = client.get("/user/by-username/$username1") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val expectedJson = Json.encodeToJsonElement(users)
        val actualJson = Json.parseToJsonElement(response.bodyAsText())

        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { userService.getUserByUsername(username1) }
    }

    @Test
    fun `POST user returns 201 when user created successfully`() = testApplication {
        val userId1 = UUID.randomUUID()
        val credentialId1 = UUID.randomUUID()

        val request = CreateUserRequest(
            username = "newuser",
            fullName = "New User",
            phoneNumber = "0712453678",
            birthDate = LocalDate.of(1990, 1, 1),
            country = "USA"
        )

        coEvery { userService.createUser(any()) } returns userId1
        coEvery { jwtService.generateJwtToken(any(), any(), any(), any()) } returns mapOf("token" to "mocked.jwt.token")

        application {
            configureTestApplication()
        }

        val token = createTestToken(userId = userId1, email = "test@yahoo.com", credentialId = credentialId1)

        val response = client.post("/user") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(CreateUserRequest.serializer(), request))
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.Created, response.status)

        val expectedJson = Json.parseToJsonElement("""{"jwtToken":"mocked.jwt.token","userId":"$userId1"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { userService.createUser(any()) }
    }

    @Test
    fun `POST user returns 500 when user creation fails`() = testApplication {
        val returned = UUID.randomUUID()
        val userId1 = UUID.randomUUID()
        val credentialId1 = UUID.randomUUID()

        val request = CreateUserRequest(
            username = "newuser",
            fullName = "New User",
            phoneNumber = "0712453678",
            birthDate = LocalDate.of(1990, 1, 1),
            country = "USA"
        )

        coEvery { userService.createUser(any()) } returns returned

        application {
            configureTestApplication()
        }

        val token = createTestToken(userId = userId1, email = "test@yahoo.com", credentialId = credentialId1)

        val response = client.post("/user") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(CreateUserRequest.serializer(), request))
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.InternalServerError, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"Failed to create user"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { userService.createUser(any()) }
    }

    @Test
    fun `PUT profile-picture returns 401 when no jwt provided`() = testApplication {
        application {
            configureTestApplication()
        }

        val request = UpdateProfilePictureRequest(imagePath = "path/to/new/picture.jpg")

        val response = client.put("/user/profile-picture") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(UpdateProfilePictureRequest.serializer(), request))
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"Invalid or missing JWT token"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `PUT profile-picture returns 404 when user not found`() = testApplication {
        val userId1 = UUID.randomUUID()
        val credentialId1 = UUID.randomUUID()

        val request = UpdateProfilePictureRequest(imagePath = "path/to/new/picture.jpg")

        coEvery { userService.updateProfilePicture(userId1, request.imagePath) } returns 0

        application {
            configureTestApplication()
        }

        val token = createTestToken(userId = userId1, email = "test@yahoo.com", credentialId = credentialId1)

        val response = client.put("/user/profile-picture") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(UpdateProfilePictureRequest.serializer(), request))
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"User not found"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { userService.updateProfilePicture(userId1, request.imagePath) }
    }

    @Test
    fun `PUT profile-picture returns 200 when update successful`() = testApplication {
        val userId1 = UUID.randomUUID()
        val credentialId1 = UUID.randomUUID()

        val request = UpdateProfilePictureRequest(imagePath = "path/to/new/picture.jpg")

        coEvery { userService.updateProfilePicture(userId1, request.imagePath) } returns 1

        application {
            configureTestApplication()
        }

        val token = createTestToken(userId = userId1, email = "test@yahoo.com", credentialId = credentialId1)

        val response = client.put("/user/profile-picture") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(UpdateProfilePictureRequest.serializer(), request))
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"Profile picture updated"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { userService.updateProfilePicture(userId1, request.imagePath) }
    }

    @Test
    fun `DELETE me returns 401 when no jwt provided`() = testApplication {
        application {
            configureTestApplication()
        }

        val response = client.delete("/user/me")

        assertEquals(HttpStatusCode.Unauthorized, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"Invalid or missing JWT token"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `DELETE me returns 404 when user not found`() = testApplication {
        val userId1 = UUID.randomUUID()
        val credentialId1 = UUID.randomUUID()

        coEvery { userService.deleteUser(userId1) } returns 0

        application {
            configureTestApplication()
        }

        val token = createTestToken(userId1, email = "test@yahoo.com", credentialId = credentialId1)

        val response = client.delete("/user/me") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"User not found"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { userService.deleteUser(userId1) }
    }

    @Test
    fun `DELETE me returns 200 when user deleted successfully`() = testApplication {
        val userId1 = UUID.randomUUID()
        val credentialId1 = UUID.randomUUID()

        coEvery { userService.deleteUser(userId1) } returns 1

        application {
            configureTestApplication()
        }

        val token = createTestToken(userId = userId1, email = "test@yahoo.com", credentialId = credentialId1)

        val response = client.delete("/user/me") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val expectedJson = Json.parseToJsonElement("""{"message":"User deleted"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { userService.deleteUser(userId1) }
    }

    private fun createTestToken(userId: UUID, isAdmin: Boolean = false, email: String, credentialId: UUID): String {
        return JWT.create()
            .withClaim("credentialId", credentialId.toString())
            .withClaim("userId", userId.toString())
            .withClaim("isAdmin", isAdmin)
            .withClaim("email", email)
            .withExpiresAt(Date(System.currentTimeMillis() + 60000))
            .sign(Algorithm.HMAC256("test-secret-key"))
    }
}
