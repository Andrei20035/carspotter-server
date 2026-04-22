package routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.carspotter.config.configureSerialization
import com.carspotter.features.friend.dto.FriendDTO
import com.carspotter.features.user.dto.UserDTO
import com.carspotter.features.friend.IFriendService
import com.carspotter.features.friend.friendRoutes
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
import org.koin.core.context.GlobalContext.startKoin
import org.koin.core.context.GlobalContext.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import java.time.Instant
import java.time.LocalDate
import java.util.*
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FriendRoutesTest : KoinTest {

    private lateinit var friendService: IFriendService

    @BeforeAll
    fun setup() {
        friendService = mockk()
    }

    @BeforeEach
    fun setupKoin() {
        startKoin {
            modules(
                module {
                    single { friendService }
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
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid JWT token"))
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
                    if (credential.payload.getClaim("isAdmin").asBoolean()) {
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
            friendRoutes()
        }
    }

    @AfterEach
    fun tearDownKoin() {
        stopKoin()
        clearAllMocks()
    }

    @Test
    fun `GET friends-admin returns 403 for non-admin users`() = testApplication {
        application {
            configureTestApplication()
        }

        val userId = UUID.randomUUID()

        val token = createTestToken(userId = userId, isAdmin = false)

        val response = client.get("/friends/admin") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"Admin access required"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `GET friends-admin returns 200 for admin users`() = testApplication {
        val friend1UserId = UUID.randomUUID()
        val friend1FriendId = UUID.randomUUID()
        val friend2UserId = UUID.randomUUID()
        val friend2FriendId = UUID.randomUUID()

        val allFriends = listOf(
            FriendDTO(userId = friend1UserId, friendId = friend1FriendId, createdAt = Instant.now()),
            FriendDTO(userId = friend2UserId, friendId = friend2FriendId, createdAt = Instant.now())
        )

        coEvery { friendService.getAllFriendsInDb() } returns allFriends

        application {
            configureTestApplication()
        }

        val token = createTestToken(userId = friend1UserId, isAdmin = true)

        val response = client.get("/friends/admin") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val expectedJson = Json.encodeToJsonElement(allFriends)
        val actualJson = Json.parseToJsonElement(response.bodyAsText())

        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { friendService.getAllFriendsInDb() }
    }


    @Test
    fun `POST friends returns 401 when JWT missing or invalid`() = testApplication {
        application {
            configureTestApplication()
        }

        val response = client.post("/friends/2") {
            header(HttpHeaders.Authorization, "Bearer invalid-token")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"Invalid JWT token"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `POST friends returns 400 for invalid friendId`() = testApplication {
        application {
            configureTestApplication()
        }

        val userId = UUID.randomUUID()

        val token = createTestToken(userId = userId)

        val response = client.post("/friends/invalid") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"Invalid or missing friendId"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `POST friends returns 400 when trying to add yourself as friend`() = testApplication {
        val userId = UUID.randomUUID()

        application {
            configureTestApplication()
        }

        val token = createTestToken(userId = userId)

        val response = client.post("/friends/$userId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"Cannot add yourself as a friend"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `POST friends returns 409 when friendship already exists`() = testApplication {
        val userId = UUID.randomUUID()
        val friendId = UUID.randomUUID()
        val returned = UUID.randomUUID()

        coEvery { friendService.addFriend(userId, friendId) } returns returned

        application {
            configureTestApplication()
        }

        val token = createTestToken(userId = userId)

        val response = client.post("/friends/$friendId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.Conflict, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"Friendship already exists"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { friendService.addFriend(userId, friendId) }
    }

    @Test
    fun `POST friends returns 201 when friend added successfully`() = testApplication {
        val userId = UUID.randomUUID()
        val friendId = UUID.randomUUID()

        coEvery { friendService.addFriend(userId, friendId) } returns friendId

        application {
            configureTestApplication()
        }

        val token = createTestToken(userId = userId)

        val response = client.post("/friends/$friendId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.Created, response.status)

        val expectedJson = Json.parseToJsonElement("""{"message":"Friend added"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { friendService.addFriend(userId, friendId) }
    }

    @Test
    fun `DELETE friends returns 401 when JWT missing or invalid`() = testApplication {
        application {
            configureTestApplication()
        }

        val response = client.delete("/friends/2") {
            header(HttpHeaders.Authorization, "Bearer invalid-token")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"Invalid JWT token"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `DELETE friends returns 400 for invalid friendId`() = testApplication {
        application {
            configureTestApplication()
        }

        val userId = UUID.randomUUID()

        val token = createTestToken(userId = userId)

        val response = client.delete("/friends/invalid") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"Invalid or missing friendId"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `DELETE friends returns 404 when friendship does not exist`() = testApplication {
        val userId = UUID.randomUUID()
        val friendId = UUID.randomUUID()

        coEvery { friendService.deleteFriend(userId, friendId) } returns 0

        application {
            configureTestApplication()
        }

        val token = createTestToken(userId = userId)

        val response = client.delete("/friends/$friendId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"Friendship does not exist"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { friendService.deleteFriend(userId, friendId) }
    }

    @Test
    fun `DELETE friends returns 200 when friend deleted successfully`() = testApplication {
        val userId = UUID.randomUUID()
        val friendId = UUID.randomUUID()

        coEvery { friendService.deleteFriend(userId, friendId) } returns 2

        application {
            configureTestApplication()
        }

        val token = createTestToken(userId = userId)

        val response = client.delete("/friends/$friendId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val expectedJson = Json.parseToJsonElement("""{"message":"Friend deleted"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { friendService.deleteFriend(userId, friendId) }
    }

    @Test
    fun `GET friends returns 401 when JWT missing or invalid`() = testApplication {
        application {
            configureTestApplication()
        }

        val response = client.get("/friends") {
            header(HttpHeaders.Authorization, "Bearer invalid-token")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"Invalid JWT token"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `GET friends returns 204 when user has no friends`() = testApplication {
        val userId = UUID.randomUUID()

        coEvery { friendService.getAllFriends(userId) } returns emptyList()

        application {
            configureTestApplication()
        }

        val token = createTestToken(userId = userId)

        val response = client.get("/friends") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.NoContent, response.status)

        coVerify(exactly = 1) { friendService.getAllFriends(userId) }
    }

    @Test
    fun `GET friends returns 200 with list of friends`() = testApplication {
        val userId1 = UUID.randomUUID()
        val userId2 = UUID.randomUUID()
        val userId3 = UUID.randomUUID()

        val friends = listOf(
            UserDTO(
                id = userId2,
                fullName = "John Doe",
                phoneNumber = "0712453678",
                username = "user2", 
                birthDate = LocalDate.of(1990, 1, 1),
                country = "USA"
            ),
            UserDTO(
                id = userId3,
                fullName = "Jane Smith",
                phoneNumber = "0712453678",
                username = "user3", 
                birthDate = LocalDate.of(1992, 5, 15),
                country = "UK"
            )
        )

        coEvery { friendService.getAllFriends(userId1) } returns friends

        application {
            configureTestApplication()
        }

        val token = createTestToken(userId = userId1)

        val response = client.get("/friends") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val expectedJson = Json.encodeToJsonElement(friends)
        val actualJson = Json.parseToJsonElement(response.bodyAsText())

        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { friendService.getAllFriends(userId1) }
    }

    private fun createTestToken(userId: UUID, isAdmin: Boolean = false): String {
        return JWT.create()
            .withClaim("userId", userId.toString())
            .withClaim("isAdmin", isAdmin)
            .withExpiresAt(Date(System.currentTimeMillis() + 60000))
            .sign(Algorithm.HMAC256("test-secret-key"))

    }
}
