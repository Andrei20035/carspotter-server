package routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.carspotter.config.configureSerialization
import com.carspotter.features.post.dto.PostDTO
import com.carspotter.features.post.dto.FeedRequest
import com.carspotter.features.post.dto.PostEditRequest
import com.carspotter.features.post.dto.PostRequest
import com.carspotter.features.post.dto.FeedResponse
import com.carspotter.features.post.IPostService
import com.carspotter.features.post.PostCreationException
import com.carspotter.features.post.postRoutes
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
import java.time.ZoneId
import java.util.*
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostRoutesTest : KoinTest {

    private lateinit var postService: IPostService

    @BeforeAll
    fun setup() {
        postService = mockk()
    }

    @BeforeEach
    fun setupKoin() {
        startKoin {
            modules(
                module {
                    single { postService }
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
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Missing or invalid JWT token"))
                }
            }
        }

        routing {
            postRoutes()
        }
    }

    @AfterEach
    fun tearDownKoin() {
        stopKoin()
        clearAllMocks()
    }

    @Test
    fun `POST posts returns 401 when JWT missing or invalid`() = testApplication {
        application {
            configureTestApplication()
        }

        val carModelId = UUID.randomUUID()

        val request = PostRequest(carModelId = carModelId, imagePath = "path/to/image.jpg", latitude = 40.0, longitude = 40.0)

        val response = client.post("/posts") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(request))
            header(HttpHeaders.Authorization, "Bearer invalid-token")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"Missing or invalid JWT token"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `POST posts returns 400 when image path is blank`() = testApplication {
        val userId = UUID.randomUUID()
        val carModelId = UUID.randomUUID()

        application {
            configureTestApplication()
        }


        val request = PostRequest(carModelId = carModelId, imagePath = "", latitude = 40.0, longitude = 40.0)

        val token = createTestToken(userId = userId)

        val response = client.post("/posts") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(request))
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"Image path cannot be blank"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `POST posts returns 400 when failed to create post due to invalid input`() = testApplication {

        val userId = UUID.randomUUID()
        val carModelId = UUID.randomUUID()

        coEvery { postService.createPost(any()) } throws PostCreationException("Failed to create post due to invalid input")

        application {
            configureTestApplication()
        }

        val request = PostRequest(carModelId = carModelId, imagePath = "path/to/image.jpg",  latitude = 40.0, longitude = 40.0)

        val token = createTestToken(userId = userId)

        val response = client.post("/posts") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(request))
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"Failed to create post due to invalid input"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { postService.createPost(any()) }
    }

    @Test
    fun `POST posts returns 200 when post created successfully`() = testApplication {
        val userId = UUID.randomUUID()
        val postId = UUID.randomUUID()
        val carModelId = UUID.randomUUID()

        coEvery { postService.createPost(any()) } returns postId

        application {
            configureTestApplication()
        }

        val request = PostRequest(carModelId = carModelId, imagePath = "path/to/image.jpg",  latitude = 40.0, longitude = 40.0)

        val token = createTestToken(userId = userId)

        val response = client.post("/posts") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(request))
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val expectedJson = Json.parseToJsonElement("""{"message":"Post created successfully"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { postService.createPost(any()) }
    }

    @Test
    fun `GET posts-postId returns 401 when JWT missing or invalid`() = testApplication {
        application {
            configureTestApplication()
        }

        val response = client.get("/posts/1") {
            header(HttpHeaders.Authorization, "Bearer invalid-token")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"Missing or invalid JWT token"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `GET posts-postId returns 400 for invalid postId`() = testApplication {
        application {
            configureTestApplication()
        }

        val token = createTestToken(userId = UUID.randomUUID())

        val response = client.get("/posts/invalid") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"Invalid postId"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `GET posts-postId returns 404 when post not found`() = testApplication {
        val userId = UUID.randomUUID()
        val postId = UUID.randomUUID()

        coEvery { postService.getPostById(postId) } returns null

        application {
            configureTestApplication()
        }

        val token = createTestToken(userId = userId)

        val response = client.get("/posts/$postId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"Post not found"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { postService.getPostById(postId) }
    }

    @Test
    fun `GET posts-postId returns 200 with post details when found`() = testApplication {
        val userId = UUID.randomUUID()
        val postId = UUID.randomUUID()
        val carModelId = UUID.randomUUID()

        val post = PostDTO(
            id = postId,
            userId = userId,
            carModelId = carModelId,
            imagePath = "path/to/image.jpg",
            description = "Test post",
            latitude = 12.3456,
            longitude = -12.3456,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        coEvery { postService.getPostById(postId) } returns post

        application {
            configureTestApplication()
        }

        val token = createTestToken(userId = userId)

        val response = client.get("/posts/$postId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val expectedJson = Json.encodeToJsonElement(post)
        val actualJson = Json.parseToJsonElement(response.bodyAsText())

        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { postService.getPostById(postId) }
    }

    @Test
    fun `GET posts returns 401 when JWT missing or invalid`() = testApplication {
        application {
            configureTestApplication()
        }

        val response = client.get("/posts") {
            header(HttpHeaders.Authorization, "Bearer invalid-token")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"Missing or invalid JWT token"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `GET posts returns 200 with list of posts`() = testApplication {
        val userId = UUID.randomUUID()
        val postId1 = UUID.randomUUID()
        val postId2 = UUID.randomUUID()
        val carModel1 = UUID.randomUUID()
        val carModel2 = UUID.randomUUID()

        val posts = listOf(
            PostDTO(
                id = postId1,
                userId = userId,
                carModelId = carModel1,
                imagePath = "path/to/image1.jpg",
                description = "Test post 1",
                latitude = 12.3456,
                longitude = -12.3456,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            ),
            PostDTO(
                id = postId2,
                userId = userId,
                carModelId = carModel2,
                imagePath = "path/to/image2.jpg",
                description = "Test post 2",
                latitude = 12.3456,
                longitude = -12.3456,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
        )

        coEvery { postService.getAllPosts() } returns posts

        application {
            configureTestApplication()
        }

        val token = createTestToken(userId = userId)

        val response = client.get("/posts") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val expectedJson = Json.encodeToJsonElement(posts)
        val actualJson = Json.parseToJsonElement(response.bodyAsText())

        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { postService.getAllPosts() }
    }

    @Test
    fun `GET posts-current-day returns 401 when JWT missing or invalid`() = testApplication {
        application {
            configureTestApplication()
        }

        val response = client.get("/posts/current-day") {
            header(HttpHeaders.Authorization, "Bearer invalid-token")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"Missing or invalid JWT token"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `GET posts-current-day returns 200 with list of posts`() = testApplication {
        val userId = UUID.randomUUID()
        val postId1 = UUID.randomUUID()
        val postId2 = UUID.randomUUID()
        val carModel1 = UUID.randomUUID()
        val carModel2 = UUID.randomUUID()

        val posts = listOf(
            PostDTO(
                id = postId1,
                userId = userId,
                carModelId = carModel1,
                imagePath = "path/to/image1.jpg",
                description = "Test post 1",
                latitude = 12.3456,
                longitude = -12.3456,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            ),
            PostDTO(
                id = postId2,
                userId = userId,
                carModelId = carModel2,
                imagePath = "path/to/image2.jpg",
                description = "Test post 2",
                latitude = 12.3456,
                longitude = -12.3456,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
        )

        coEvery { postService.getCurrentDayPostsForUser(userId, any()) } returns posts

        application {
            configureTestApplication()
        }

        val token = createTestToken(userId = userId)

        val response = client.get("/posts/current-day") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header("Time-Zone", "UTC")
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val expectedJson = Json.encodeToJsonElement(posts)
        val actualJson = Json.parseToJsonElement(response.bodyAsText())

        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { postService.getCurrentDayPostsForUser(userId, ZoneId.of("UTC")) }
    }

    @Test
    fun `PUT posts-postId returns 401 when JWT missing or invalid`() = testApplication {
        application {
            configureTestApplication()
        }

        val request = PostEditRequest(newDescription = "Updated description")

        val response = client.put("/posts/1") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(request))
            header(HttpHeaders.Authorization, "Bearer invalid-token")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"Missing or invalid JWT token"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `PUT posts-postId returns 400 for invalid postId`() = testApplication {
        application {
            configureTestApplication()
        }

        val token = createTestToken(userId = UUID.randomUUID())
        val request = PostEditRequest(newDescription = "Updated description")

        val response = client.put("/posts/invalid") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(request))
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"Invalid or missing postId"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `PUT posts-postId returns 403 when user doesn't have permission to edit`() = testApplication {
        val userId = UUID.randomUUID()
        val postId = UUID.randomUUID()
        val postOwnerId = UUID.randomUUID()

        coEvery { postService.getUserIdByPost(postId) } returns postOwnerId

        application {
            configureTestApplication()
        }

        val token = createTestToken(userId = userId)
        val request = PostEditRequest(newDescription = "Updated description")

        val response = client.put("/posts/$postId") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(request))
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"You do not have permission to edit this post"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { postService.getUserIdByPost(postId) }
    }

    @Test
    fun `PUT posts-postId returns 404 when post not found or failed to update`() = testApplication {
        val userId = UUID.randomUUID()
        val postId = UUID.randomUUID()

        coEvery { postService.getUserIdByPost(postId) } returns userId
        coEvery { postService.editPost(postId, "Updated description") } returns 0

        application {
            configureTestApplication()
        }

        val token = createTestToken(userId = userId)
        val request = PostEditRequest(newDescription = "Updated description")

        val response = client.put("/posts/$postId") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(request))
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"Post not found or failed to update"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { postService.getUserIdByPost(postId) }
        coVerify(exactly = 1) { postService.editPost(postId, "Updated description") }
    }

    @Test
    fun `PUT posts-postId returns 200 when post updated successfully`() = testApplication {
        val userId = UUID.randomUUID()
        val postId = UUID.randomUUID()

        coEvery { postService.getUserIdByPost(postId) } returns userId
        coEvery { postService.editPost(postId, "Updated description") } returns 1

        application {
            configureTestApplication()
        }

        val token = createTestToken(userId = userId)
        val request = PostEditRequest(newDescription = "Updated description")

        val response = client.put("/posts/$postId") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(request))
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val expectedJson = Json.parseToJsonElement("""{"message":"Post updated successfully"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { postService.getUserIdByPost(postId) }
        coVerify(exactly = 1) { postService.editPost(postId, "Updated description") }
    }

    @Test
    fun `DELETE posts-postId returns 401 when JWT missing or invalid`() = testApplication {
        application {
            configureTestApplication()
        }

        val response = client.delete("/posts/1") {
            header(HttpHeaders.Authorization, "Bearer invalid-token")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"Missing or invalid JWT token"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `DELETE posts-postId returns 400 for invalid postId`() = testApplication {
        application {
            configureTestApplication()
        }

        val token = createTestToken(userId = UUID.randomUUID())

        val response = client.delete("/posts/invalid") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"Invalid postId"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `DELETE posts-postId returns 403 when user doesn't have permission to delete`() = testApplication {
        val userId = UUID.randomUUID()
        val postId = UUID.randomUUID()
        val postOwnerId = UUID.randomUUID()


        coEvery { postService.getUserIdByPost(postId) } returns postOwnerId

        application {
            configureTestApplication()
        }

        val token = createTestToken(userId = userId)

        val response = client.delete("/posts/$postId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"You do not have permission to edit this post"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { postService.getUserIdByPost(postId) }
    }

    @Test
    fun `DELETE posts-postId returns 404 when post not found or already deleted`() = testApplication {
        val userId = UUID.randomUUID()
        val postId = UUID.randomUUID()

        coEvery { postService.getUserIdByPost(postId) } returns userId
        coEvery { postService.deletePost(postId) } returns 0

        application {
            configureTestApplication()
        }

        val token = createTestToken(userId = userId)

        val response = client.delete("/posts/$postId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)

        val expectedJson = Json.parseToJsonElement("""{"error":"Post not found or already deleted"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { postService.getUserIdByPost(postId) }
        coVerify(exactly = 1) { postService.deletePost(postId) }
    }

    @Test
    fun `DELETE posts-postId returns 200 when post deleted successfully`() = testApplication {
        val userId = UUID.randomUUID()
        val postId = UUID.randomUUID()

        coEvery { postService.getUserIdByPost(postId) } returns userId
        coEvery { postService.deletePost(postId) } returns 1

        application {
            configureTestApplication()
        }

        val token = createTestToken(userId = userId)

        val response = client.delete("/posts/$postId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val expectedJson = Json.parseToJsonElement("""{"message":"Post deleted successfully"}""").jsonObject
        val actualJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(expectedJson, actualJson)

        coVerify(exactly = 1) { postService.getUserIdByPost(postId) }
        coVerify(exactly = 1) { postService.deletePost(postId) }
    }

    @Test
    fun `GET feed returns 200 and feed response when request is valid`() = testApplication {
        application {
            configureTestApplication()
        }

        val userId = UUID.randomUUID()

        val mockFeedResponse = FeedResponse(
            posts = listOf(
                PostDTO(
                    id = UUID.randomUUID(),
                    userId = userId,
                    carModelId = UUID.randomUUID(),
                    imagePath = "path/to/image.jpg",
                    latitude = 40.0,
                    longitude = -74.0,
                    description = "Mock post",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now()
                )
            ),
            nextCursor = null,
            hasMore = false
        )

        coEvery {
            postService.getFeedPostsForUser(
                userId = userId,
                latitude = 40.0,
                longitude = -74.0,
                radiusKm = 5,
                limit = 10,
                cursor = null,
            )
        } returns mockFeedResponse

        val request = FeedRequest(
            latitude = 40.0,
            longitude = -74.0,
            radiusKm = 5,
            limit = 10,
            userId = userId,
        )

        val token = createTestToken(userId)

        val response = client.get("/posts/feed") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(request))
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val returnedFeed = Json.decodeFromString<FeedResponse>(response.bodyAsText())
        assertEquals(mockFeedResponse.posts.size, returnedFeed.posts.size)
        assertEquals(mockFeedResponse.posts.first().description, returnedFeed.posts.first().description)
    }

    @Test
    fun `GET feed returns 401 when JWT is missing or invalid`() = testApplication {
        application {
            configureTestApplication()
        }

        val response = client.get("/posts/feed") {
            header(HttpHeaders.Authorization, "Bearer invalid.token.here")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        val expectedJson = Json.parseToJsonElement("""{"error":"Missing or invalid JWT token"}""")
        val actualJson = Json.parseToJsonElement(response.bodyAsText())
        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun `GET feed returns 500 when service throws exception`() = testApplication {
        application {
            configureTestApplication()
        }

        val userId = UUID.randomUUID()

        val jwt = JWT.create()
            .withClaim("userId", userId.toString())
            .sign(Algorithm.HMAC256("test-secret-key"))

        val request = FeedRequest(
            latitude = 40.0,
            longitude = -74.0,
            radiusKm = 5,
            limit = 10,
            cursor = null
        )

        coEvery {
            postService.getFeedPostsForUser(any(), any(), any(), any(), any(), any())
        } throws RuntimeException("Service failed")

        val response = client.get("/posts/feed") {
            header(HttpHeaders.Authorization, "Bearer $jwt")
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(request))
        }

        assertEquals(HttpStatusCode.InternalServerError, response.status)
        val expectedJson = Json.parseToJsonElement("""{"error":"Unable to fetch feed. Please try again later."}""")
        val actualJson = Json.parseToJsonElement(response.bodyAsText())
        assertEquals(expectedJson, actualJson)
    }




    private fun createTestToken(userId: UUID): String {
        return JWT.create()
            .withClaim("userId", userId.toString())
            .withExpiresAt(Date(System.currentTimeMillis() + 60000))
            .sign(Algorithm.HMAC256("test-secret-key"))

    }
}