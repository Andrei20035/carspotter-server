package routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.carspotter.config.configureSerialization
import com.carspotter.features.user.dto.UserDTO
import com.carspotter.features.like.DuplicateLikeException
import com.carspotter.features.like.ILikeService
import com.carspotter.features.like.likeRoutes
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
import java.time.LocalDate
import java.util.*
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LikeRoutesTest : KoinTest {

    private lateinit var likeService: ILikeService

    @BeforeAll
    fun setup() {
        likeService = mockk()
    }

    @BeforeEach
    fun setupKoin() {
        startKoin {
            modules(
                module {
                    single { likeService }
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
        }

        routing {
            likeRoutes()
        }
    }

    @AfterEach
    fun tearDownKoin() {
        stopKoin()
        clearAllMocks()
    }

    @Test
    fun `POST likes returns 401 when JWT missing or invalid`() = testApplication {
        application {
            configureTestApplication()
        }

        val response = client.post("/likes/1") {
            header(HttpHeaders.Authorization, "Bearer invalid-token")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"Invalid JWT token"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `POST likes returns 400 for invalid postId`() = testApplication {
        application {
            configureTestApplication()
        }

        val userId = UUID.randomUUID()

        val token = createTestToken(userId = userId)

        val response = client.post("/likes/invalid") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"Invalid or missing postId"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `POST likes returns 409 when user has already liked the post`() = testApplication {

        val userId = UUID.randomUUID()
        val postId = UUID.randomUUID()

        coEvery { likeService.likePost(userId, postId) } throws DuplicateLikeException("User $userId has already liked post $postId")

        application {
            configureTestApplication()
        }

        val token = createTestToken(userId = userId)

        val response = client.post("/likes/$postId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.Conflict, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"You have already liked this post"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { likeService.likePost(userId, postId) }
    }

    @Test
    fun `POST likes returns 200 when post liked successfully`() = testApplication {
        val userId = UUID.randomUUID()
        val postId = UUID.randomUUID()
        val returned = UUID.randomUUID()

        coEvery { likeService.likePost(userId, postId) } returns returned

        application {
            configureTestApplication()
        }

        val token = createTestToken(userId = userId)

        val response = client.post("/likes/$postId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val expectedJson = Json.parseToJsonElement("""{"message":"Post liked successfully"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { likeService.likePost(userId, postId) }
    }

    @Test
    fun `DELETE likes returns 401 when JWT missing or invalid`() = testApplication {
        application {
            configureTestApplication()
        }

        val response = client.delete("/likes/1") {
            header(HttpHeaders.Authorization, "Bearer invalid-token")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"Invalid JWT token"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `DELETE likes returns 400 for invalid postId`() = testApplication {
        application {
            configureTestApplication()
        }

        val userId = UUID.randomUUID()

        val token = createTestToken(userId = userId)

        val response = client.delete("/likes/invalid") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"Invalid or missing postId"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `DELETE likes returns 404 when like not found or already removed`() = testApplication {
        val userId = UUID.randomUUID()
        val postId = UUID.randomUUID()

        coEvery { likeService.unlikePost(userId, postId) } returns 0

        application {
            configureTestApplication()
        }

        val token = createTestToken(userId = userId)

        val response = client.delete("/likes/$postId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"Like not found or already removed"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { likeService.unlikePost(userId, postId) }
    }

    @Test
    fun `DELETE likes returns 200 when post unliked successfully`() = testApplication {
        val userId = UUID.randomUUID()
        val postId = UUID.randomUUID()

        coEvery { likeService.unlikePost(userId, postId) } returns 1

        application {
            configureTestApplication()
        }

        val token = createTestToken(userId = userId)

        val response = client.delete("/likes/$postId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val expectedJson = Json.parseToJsonElement("""{"message":"Post unliked successfully"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { likeService.unlikePost(userId, postId) }
    }

    @Test
    fun `GET likes-posts returns 401 when JWT missing or invalid`() = testApplication {
        application {
            configureTestApplication()
        }

        val response = client.get("/likes/posts/1") {
            header(HttpHeaders.Authorization, "Bearer invalid-token")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"Invalid JWT token"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `GET likes-posts returns 400 for invalid postId`() = testApplication {
        application {
            configureTestApplication()
        }

        val userId = UUID.randomUUID()

        val token = createTestToken(userId = userId)

        val response = client.get("/likes/posts/invalid") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"Invalid or missing postId"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `GET likes-posts returns 204 when no likes for the post`() = testApplication {
        val userId = UUID.randomUUID()
        val postId = UUID.randomUUID()

        coEvery { likeService.getLikesForPost(postId) } returns emptyList()

        application {
            configureTestApplication()
        }

        val token = createTestToken(userId = userId)

        val response = client.get("/likes/posts/$postId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.NoContent, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"No likes for this post"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { likeService.getLikesForPost(postId) }
    }

    @Test
    fun `GET likes-posts returns 200 with list of users who liked the post`() = testApplication {
        val userId = UUID.randomUUID()
        val postId = UUID.randomUUID()
        val userId2 = UUID.randomUUID()
        val userId3 = UUID.randomUUID()

        val users = listOf(
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

        coEvery { likeService.getLikesForPost(postId) } returns users

        application {
            configureTestApplication()
        }

        val token = createTestToken(userId = userId)

        val response = client.get("/likes/posts/$postId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val expectedJson = Json.encodeToJsonElement(users)
        val actualJson = Json.parseToJsonElement(response.bodyAsText())

        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { likeService.getLikesForPost(postId) }
    }

    private fun createTestToken(userId: UUID): String {
        return JWT.create()
            .withClaim("userId", userId.toString())
            .withExpiresAt(Date(System.currentTimeMillis() + 60000))
            .sign(Algorithm.HMAC256("test-secret-key"))

    }
}