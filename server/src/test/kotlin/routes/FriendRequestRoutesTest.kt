package routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.carspotter.config.configureSerialization
import com.carspotter.features.user.dto.UserDTO
import com.carspotter.features.comment.dto.toDTO
import com.carspotter.features.friend_request.FriendRequest
import com.carspotter.features.friend_request.IFriendRequestService
import com.carspotter.features.friend_request.friendRequestRoutes
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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.*
import org.koin.core.context.GlobalContext.startKoin
import org.koin.core.context.GlobalContext.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FriendRequestRoutesTest : KoinTest {
    private lateinit var friendRequestService: IFriendRequestService

    @BeforeAll
    fun setup() {
        friendRequestService = mockk()
    }

    @BeforeEach
    fun setupKoin() {
        startKoin {
            modules(
                module {
                    single { friendRequestService }
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
                challenge { defaultScheme, realm ->
                    val authHeader = call.request.headers[HttpHeaders.Authorization]

                    val message = if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                        "Missing JWT token"
                    } else {
                        "Invalid JWT token"
                    }

                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to message))
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
                    } else null
                }
                challenge { _, _ ->
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin access required"))
                }
            }
        }

        routing {
            friendRequestRoutes()
        }
    }

    @AfterEach
    fun tearDownKoin() {
        stopKoin()
        clearAllMocks()
    }

    @Test
    fun `GET admin friend requests returns 403 if not admin`() = testApplication {
        application {
            configureTestApplication()
        }

        val userId = UUID.randomUUID()

        val token = JWT.create()
            .withClaim("userId", userId.toString())
            .withClaim("isAdmin", false)
            .sign(Algorithm.HMAC256("test-secret-key"))

        val response = client.get("/friend-requests/admin") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        val expectedJson = Json.encodeToJsonElement(mapOf("error" to "Admin access required")).jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)
        assertEquals(HttpStatusCode.Forbidden, response.status)
        coVerify(exactly = 0) { friendRequestService.getAllFriendReqFromDB() }
    }

    @Test
    fun `GET admin friend requests returns 200 OK`() = testApplication {
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()
        val id3 = UUID.randomUUID()

        val mockData = listOf(
            FriendRequest(id1, id2, createdAt = Instant.now()).toDTO(),
            FriendRequest(id2, id3, createdAt = Instant.now()).toDTO()
        )
        coEvery { friendRequestService.getAllFriendReqFromDB() } returns mockData

        application {
            configureTestApplication()
        }

        val token = JWT.create()
            .withClaim("userId", id1.toString())
            .withClaim("isAdmin", true)
            .sign(Algorithm.HMAC256("test-secret-key"))

        val response = client.get("/friend-requests/admin") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val expectedJson = Json.encodeToJsonElement(mockData).jsonArray
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonArray

        assertEquals(expectedJson, actualJson)
        coVerify(exactly = 1) { friendRequestService.getAllFriendReqFromDB() }
    }


    @Test
    fun `POST friend request returns 401 if invalid JWT`() = testApplication {
        application {
            configureTestApplication()
        }

        val response = client.post("/friend-requests/1") {
            header(HttpHeaders.Authorization, "Bearer invalid-token")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `POST friend request returns 401 if no JWT`() = testApplication {
        application {
            configureTestApplication()
        }

        val response = client.post("/friend-requests/1") {
            header(HttpHeaders.Authorization, "Bearer ")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `POST friend request returns 400 if invalid receiverId`() = testApplication {
        application {
            configureTestApplication()
        }

        val userId = UUID.randomUUID()

        val token = JWT.create()
            .withClaim("userId", userId.toString())
            .sign(Algorithm.HMAC256("test-secret-key"))

        val response = client.post("/friend-requests/abc") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST friend request returns 400 if receiverId is same as senderId`() = testApplication {
        application {
            configureTestApplication()
        }
        val userId = UUID.randomUUID()

        val token = JWT.create()
            .withClaim("userId", userId.toString())
            .sign(Algorithm.HMAC256("test-secret-key"))

        val response = client.post("/friend-requests/$userId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)

        val expectedJson = Json.encodeToJsonElement(mapOf("error" to "You cannot send a friend request to yourself")).jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)

    }

    @Test
    fun `POST friend request returns 201 when successful`() = testApplication {
        val senderId = UUID.randomUUID()
        val receiverId = UUID.randomUUID()
        val returned = UUID.randomUUID()

        coEvery { friendRequestService.sendFriendRequest(senderId, receiverId) } returns returned

        application {
            configureTestApplication()
        }
        val token = JWT.create()
            .withClaim("userId", senderId.toString()) // UUID as string in claim
            .sign(Algorithm.HMAC256("test-secret-key"))

        val response = client.post("/friend-requests/${receiverId}") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        coVerify(exactly = 1) { friendRequestService.sendFriendRequest(senderId, receiverId) }
    }

    @Test
    fun `POST accept friend request returns 401 when JWT invalid`() = testApplication {
        val senderId = 1

        application {
            configureTestApplication()
        }

        val response = client.post("friend-requests/$senderId/accept") {
            header(HttpHeaders.Authorization, "Bearer invalid-token")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)

        val expectedJson = Json.encodeToJsonElement(mapOf("error" to "Invalid JWT token")).jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `POST accept friend request returns 401 when no JWT`() = testApplication {
        val senderId = 1

        application {
            configureTestApplication()
        }

        val response = client.post("friend-requests/$senderId/accept") {
            header(HttpHeaders.Authorization, null)
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)

        val expectedJson = Json.encodeToJsonElement(mapOf("error" to "Missing JWT token")).jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)

    }

    @Test
    fun `POST accept friend request returns 400 when invalid senderId`() = testApplication {
        application {
            configureTestApplication()
        }

        val userId = UUID.randomUUID()

        val token = JWT.create()
            .withClaim("userId", userId.toString())
            .sign(Algorithm.HMAC256("test-secret-key"))

        val response = client.post("friend-requests/abc/accept") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)

        val expectedJson = Json.encodeToJsonElement(mapOf("error" to "Invalid or missing senderId")).jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `POST accept friend request returns 400 when senderId matches receiverId`() = testApplication {
        val senderId = UUID.randomUUID()

        application {
            configureTestApplication()
        }

        val token = JWT.create()
            .withClaim("userId", senderId.toString())
            .sign(Algorithm.HMAC256("test-secret-key"))

        val response = client.post("friend-requests/$senderId/accept") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)

        val expectedJson = Json.encodeToJsonElement(mapOf("error" to "You cannot accept a friend request from yourself")).jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `POST accept friend request returns 200 when friend request is accepted`() = testApplication {
        val senderId = UUID.randomUUID()
        val receiverId = UUID.randomUUID()

        coEvery { friendRequestService.acceptFriendRequest(senderId, receiverId) } returns true

        application {
            configureTestApplication()
        }

        val token = JWT.create()
            .withClaim("userId", receiverId.toString())
            .sign(Algorithm.HMAC256("test-secret-key"))

        val response = client.post("friend-requests/$senderId/accept") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val expectedJson = Json.encodeToJsonElement(mapOf("message" to "Friend request accepted")).jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { friendRequestService.acceptFriendRequest(senderId, receiverId) }
    }

    @Test
    fun `POST decline friend request returns 404 when JWT is invalid`() = testApplication {
        val senderId = 1

        application {
            configureTestApplication()
        }

        val response = client.post("friend-requests/$senderId/decline") {
            header(HttpHeaders.Authorization, "Bearer invalid")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)

        val expectedJson = Json.encodeToJsonElement(mapOf("error" to "Invalid JWT token")).jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `POST decline friend request returns 404 when missing JWT`() = testApplication {
        val senderId = 1

        application {
            configureTestApplication()
        }

        val response = client.post("friend-requests/$senderId/decline") {
            header(HttpHeaders.Authorization, null)
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)

        val expectedJson = Json.encodeToJsonElement(mapOf("error" to "Missing JWT token")).jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `POST decline friend request returns 401 when invalid or missing JWT`() = testApplication {
        val senderId = UUID.randomUUID()

        application {
            configureTestApplication()
        }

        val response = client.post("friend-requests/$senderId/decline") {
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `POST decline friend request returns 400 when senderId matches receiverId`() = testApplication {

        val senderId = UUID.randomUUID()

        application {
            configureTestApplication()
        }

        val token = JWT.create()
            .withClaim("userId", senderId.toString())
            .sign(Algorithm.HMAC256("test-secret-key"))

        val response = client.post("friend-requests/$senderId/decline") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)

        val expectedJson = Json.encodeToJsonElement(mapOf("error" to "You cannot decline a friend request from yourself")).jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `POST decline friend request returns 200 when friend request declined`() = testApplication {
        val senderId = UUID.randomUUID()
        val receiverId = UUID.randomUUID()

        coEvery { friendRequestService.declineFriendRequest(senderId, receiverId) } returns 1

        application {
            configureTestApplication()
        }

        val token = JWT.create()
            .withClaim("userId", receiverId.toString())
            .sign(Algorithm.HMAC256("test-secret-key"))

        val response = client.post("/friend-requests/$senderId/decline") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val expectedJson = Json.encodeToJsonElement(mapOf("message" to "Friend request declined")).jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { friendRequestService.declineFriendRequest(senderId, receiverId) }
    }

    @Test
    fun `GET friend request returns for user returns 401 when JWT invalid`() = testApplication {
        application {
            configureTestApplication()
        }

        val response = client.get("/friend-requests") {
            header(HttpHeaders.Authorization, "Bearer invalid-token")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)

        val expectedJson = Json.encodeToJsonElement(mapOf("error" to "Invalid JWT token")).jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `GET friend request for user returns 401 when no JWT`() = testApplication {
        val senderId = 1

        application {
            configureTestApplication()
        }

        val response = client.get("/friend-requests") {
            header(HttpHeaders.Authorization, null)
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)

        val expectedJson = Json.encodeToJsonElement(mapOf("error" to "Missing JWT token")).jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)

    }

    @Test
    fun `GET friend request for user returns 204 when empty`() = testApplication {
        val userId = UUID.randomUUID()

        val friendRequests = emptyList<UserDTO>()

        coEvery { friendRequestService.getAllFriendRequests(userId) } returns friendRequests

        application {
            configureTestApplication()
        }

        val token = JWT.create()
            .withClaim("userId", userId.toString())
            .sign(Algorithm.HMAC256("test-secret-key"))

        val response = client.get("/friend-requests") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.NoContent, response.status)

        coVerify(exactly = 1) { friendRequestService.getAllFriendRequests(userId) }
    }
}